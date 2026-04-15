package com.huochai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GitHubTrendService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${github.api.token:}")
    private String githubToken;

    @Value("${github.api.base-url:https://api.github.com}")
    private String baseUrl;

    // 本地缓存（减少对 Redis 的依赖，同时实现快速访问）
    private final Map<String, RepoStarsHistory> localCache = new ConcurrentHashMap<>();

    // 请求限流计数器
    private final Map<String, Integer> rateLimitRemaining = new ConcurrentHashMap<>();
    private long rateLimitResetTime = 0;

    // 已知的主流 Java 技术栈仓库映射（技术名称 -> GitHub 仓库全名）
    private static final Map<String, String> TECH_REPO_MAPPING = new HashMap<>();

    static {
        TECH_REPO_MAPPING.put("Spring Boot", "spring-projects/spring-boot");
        TECH_REPO_MAPPING.put("Spring Cloud", "spring-cloud/spring-cloud-commons");
        TECH_REPO_MAPPING.put("Redis", "redis/redis");
        TECH_REPO_MAPPING.put("Kafka", "apache/kafka");
        TECH_REPO_MAPPING.put("Elasticsearch", "elastic/elasticsearch");
        TECH_REPO_MAPPING.put("RabbitMQ", "rabbitmq/rabbitmq-server");
        TECH_REPO_MAPPING.put("MySQL", "mysql/mysql-server");
        TECH_REPO_MAPPING.put("MongoDB", "mongodb/mongo");
        TECH_REPO_MAPPING.put("Docker", "docker/engine");
        TECH_REPO_MAPPING.put("Kubernetes", "kubernetes/kubernetes");
        TECH_REPO_MAPPING.put("Dubbo", "apache/dubbo");
        TECH_REPO_MAPPING.put("Netty", "netty/netty");
        TECH_REPO_MAPPING.put("Tomcat", "apache/tomcat");
        TECH_REPO_MAPPING.put("Nginx", "nginx/nginx");
        TECH_REPO_MAPPING.put("Prometheus", "prometheus/prometheus");
        TECH_REPO_MAPPING.put("Grafana", "grafana/grafana");
    }

    public GitHubTrendService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 获取指定技术栈的 Star 增长趋势
     * @param techNames 技术名称列表
     * @return 技术名称 -> 增长率（百分比）
     */
    @Cacheable(value = "github-trends", unless = "#result == null || #result.isEmpty()")
    public Map<String, Double> getStarGrowthTrends(List<String> techNames) {
        if (techNames == null || techNames.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Double> results = new ConcurrentHashMap<>();

        // 并行请求提升性能
        techNames.parallelStream().forEach(techName -> {
            String repoFullName = resolveRepoName(techName);
            if (repoFullName == null) {
                log.warn("No GitHub repo mapping found for tech: {}", techName);
                results.put(techName, 0.0);
                return;
            }

            try {
                Double growthRate = calculateStarGrowthRate(repoFullName);
                results.put(techName, growthRate);
            } catch (Exception e) {
                log.error("Failed to fetch trend for {}: {}", techName, e.getMessage());
                results.put(techName, 0.0);
            }
        });

        return results;
    }

    /**
     * 获取单个仓库的 Star 增长趋势（公开方法，供外部调用）
     */
    public StarTrendInfo getRepoTrend(String repoFullName, int lookbackDays) {
        try {
            // 获取当前 Star 数
            int currentStars = fetchCurrentStars(repoFullName);

            // 获取历史 Star 数据
            RepoStarsHistory history = fetchStarHistory(repoFullName);

            // 计算增长率
            double growthRate = calculateGrowthRate(history, lookbackDays);

            // 判断趋势分类
            TrendCategory category = classifyTrend(growthRate, history);

            // 计算增长速度（stars/day）
            double velocity = calculateVelocity(history, lookbackDays);

            return StarTrendInfo.builder()
                    .repoFullName(repoFullName)
                    .currentStars(currentStars)
                    .growthRate(growthRate)
                    .velocity(velocity)
                    .trendCategory(category)
                    .dataPoints(history.getDailyStars())
                    .lastUpdated(history.getLastUpdated())
                    .build();

        } catch (Exception e) {
            log.error("Failed to get repo trend for {}: {}", repoFullName, e.getMessage());
            return StarTrendInfo.empty(repoFullName);
        }
    }

    /**
     * 获取当前 Star 数量
     */
    private int fetchCurrentStars(String repoFullName) {
        String url = baseUrl + "/repos/" + repoFullName;

        HttpHeaders headers = buildHeaders();
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            updateRateLimitInfo(response.getHeaders());

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.path("stargazers_count").asInt();
            }
        } catch (Exception e) {
            log.error("Failed to fetch current stars for {}: {}", repoFullName, e.getMessage());
        }
        return 0;
    }

    /**
     * 计算 Star 增长率
     * @param repoFullName GitHub 仓库全名（如 "spring-projects/spring-boot"）
     * @return 增长率（百分比，如 15.5 表示 15.5%）
     */
    public Double calculateStarGrowthRate(String repoFullName) {
        // 检查缓存
        if (localCache.containsKey(repoFullName)) {
            RepoStarsHistory cached = localCache.get(repoFullName);
            // 缓存 1 小时内有效
            if (System.currentTimeMillis() - cached.getTimestamp() < 3600000) {
                return calculateGrowthRate(cached, 30);
            }
        }

        RepoStarsHistory history = fetchStarHistory(repoFullName);
        return calculateGrowthRate(history, 30);
    }

    /**
     * 获取仓库的历史 Star 数据
     * 使用 GitHub API 的 stargazers 端点获取
     */
    private RepoStarsHistory fetchStarHistory(String repoFullName) {
        RepoStarsHistory history = new RepoStarsHistory();
        history.setRepoFullName(repoFullName);
        history.setDailyStars(new LinkedHashMap<>());

        // 获取当前总 Star 数
        int currentStars = fetchCurrentStars(repoFullName);

        // 通过 stargazers 端点获取近期的 Star 记录
        // GitHub API 限制：未认证每小时 60 次，已认证每小时 5000 次
        List<StarEvent> recentStars = fetchRecentStargazers(repoFullName);

        // 按日期聚合
        Map<LocalDate, Integer> dailyCount = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();

        // 初始化近 90 天的数据
        for (int i = 90; i >= 0; i--) {
            dailyCount.put(today.minusDays(i), 0);
        }

        // 统计每日新增
        for (StarEvent event : recentStars) {
            LocalDate starDate = LocalDate.ofInstant(event.getStarredAt(), ZoneId.systemDefault());
            dailyCount.merge(starDate, 1, Integer::sum);
        }

        // 如果近期记录不足以覆盖 90 天，使用估算方法
        if (recentStars.size() < 500) {
            estimateHistoricalStars(dailyCount, currentStars);
        }

        history.setDailyStars(dailyCount);
        history.setLastUpdated(new Date());
        history.setTimestamp(System.currentTimeMillis());

        // 存入本地缓存
        localCache.put(repoFullName, history);

        return history;
    }

    /**
     * 获取近期的 Star 记录
     */
    private List<StarEvent> fetchRecentStargazers(String repoFullName) {
        List<StarEvent> events = new ArrayList<>();
        int page = 1;
        int perPage = 100;
        boolean hasMore = true;

        while (hasMore && page <= 5) { // 最多获取 500 条记录
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/repos/" + repoFullName + "/stargazers")
                    .queryParam("per_page", perPage)
                    .queryParam("page", page)
                    .build()
                    .toUriString();

            HttpHeaders headers = buildHeaders();
            headers.set("Accept", "application/vnd.github.v3.star+json"); // 获取带时间的 star 信息
            HttpEntity<String> entity = new HttpEntity<>(headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);

                updateRateLimitInfo(response.getHeaders());

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    JsonNode root = objectMapper.readTree(response.getBody());

                    if (root.isArray() && root.size() > 0) {
                        for (JsonNode node : root) {
                            StarEvent event = new StarEvent();
                            event.setUser(node.path("user").path("login").asText());
                            String starredAt = node.path("starred_at").asText();
                            if (!starredAt.isEmpty()) {
                                event.setStarredAt(Instant.parse(starredAt));
                                events.add(event);
                            }
                        }

                        // 检查是否有下一页
                        String linkHeader = response.getHeaders().getFirst("Link");
                        hasMore = linkHeader != null && linkHeader.contains("rel=\"next\"");
                    } else {
                        hasMore = false;
                    }
                }

                // 遵守 GitHub API 限流
                checkAndWaitForRateLimit();

            } catch (Exception e) {
                log.error("Failed to fetch stargazers: {}", e.getMessage());
                hasMore = false;
            }

            page++;
        }

        return events;
    }

    /**
     * 估算历史 Star 数据（当 API 返回数据不足时）
     */
    private void estimateHistoricalStars(Map<LocalDate, Integer> dailyCount, int currentStars) {
        // 简单估算：假设 Star 增长服从线性分布
        int totalFromEvents = dailyCount.values().stream().mapToInt(Integer::intValue).sum();
        if (totalFromEvents == 0) {
            return;
        }

        // 按比例放大到当前总 Star 数
        double ratio = (double) currentStars / totalFromEvents;
        dailyCount.replaceAll((date, count) -> (int) (count * ratio));
    }

    /**
     * 计算增长率
     */
    private Double calculateGrowthRate(RepoStarsHistory history, int lookbackDays) {
        Map<LocalDate, Integer> dailyStars = history.getDailyStars();
        if (dailyStars == null || dailyStars.isEmpty()) {
            return 0.0;
        }

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(lookbackDays);

        // 获取近 7 天的平均新增 Star
        int recentSum = 0;
        int recentDays = 0;
        for (int i = 1; i <= 7; i++) {
            LocalDate date = today.minusDays(i);
            Integer count = dailyStars.getOrDefault(date, 0);
            if (count > 0) {
                recentSum += count;
                recentDays++;
            }
        }
        double recentAvg = recentDays > 0 ? (double) recentSum / recentDays : 0;

        // 获取之前 23 天的平均新增 Star
        int earlierSum = 0;
        int earlierDays = 0;
        for (int i = 8; i <= 30; i++) {
            LocalDate date = today.minusDays(i);
            Integer count = dailyStars.getOrDefault(date, 0);
            if (count > 0) {
                earlierSum += count;
                earlierDays++;
            }
        }
        double earlierAvg = earlierDays > 0 ? (double) earlierSum / earlierDays : 1;

        // 计算增长率
        if (earlierAvg == 0) {
            return recentAvg > 0 ? 100.0 : 0.0;
        }

        return ((recentAvg - earlierAvg) / earlierAvg) * 100;
    }

    /**
     * 计算增长速度（stars/day）
     */
    private double calculateVelocity(RepoStarsHistory history, int lookbackDays) {
        Map<LocalDate, Integer> dailyStars = history.getDailyStars();
        LocalDate today = LocalDate.now();

        int total = 0;
        int validDays = 0;
        for (int i = 1; i <= Math.min(lookbackDays, 30); i++) {
            LocalDate date = today.minusDays(i);
            Integer count = dailyStars.getOrDefault(date, 0);
            if (count > 0) {
                total += count;
                validDays++;
            }
        }

        return validDays > 0 ? (double) total / validDays : 0.0;
    }

    /**
     * 判断趋势分类
     */
    private TrendCategory classifyTrend(double growthRate, RepoStarsHistory history) {
        if (growthRate > 50) {
            return TrendCategory.VIRAL;
        } else if (growthRate > 20) {
            return TrendCategory.ACCELERATING;
        } else if (growthRate > 5) {
            return TrendCategory.GROWING;
        } else if (growthRate > -5) {
            return TrendCategory.STABLE;
        } else {
            return TrendCategory.DECLINING;
        }
    }

    /**
     * 构建 HTTP 请求头
     */
    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("User-Agent", "Java-Engineer-Growth-System");

        if (githubToken != null && !githubToken.isEmpty()) {
            headers.set("Authorization", "Bearer " + githubToken);
        }

        return headers;
    }

    /**
     * 更新 API 限流信息
     */
    private void updateRateLimitInfo(HttpHeaders headers) {
        String remaining = headers.getFirst("X-RateLimit-Remaining");
        String reset = headers.getFirst("X-RateLimit-Reset");

        if (remaining != null) {
            rateLimitRemaining.put("core", Integer.parseInt(remaining));
        }
        if (reset != null) {
            rateLimitResetTime = Long.parseLong(reset);
        }
    }

    /**
     * 检查并等待 API 限流
     */
    private void checkAndWaitForRateLimit() {
        int remaining = rateLimitRemaining.getOrDefault("core", 60);
        if (remaining < 5) {
            long waitSeconds = Math.max(1, rateLimitResetTime - System.currentTimeMillis() / 1000);
            log.warn("GitHub API rate limit low ({} remaining). Waiting {} seconds...",
                    remaining, waitSeconds);
            try {
                TimeUnit.SECONDS.sleep(Math.min(waitSeconds, 60));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 解析技术名称到 GitHub 仓库全名
     */
    private String resolveRepoName(String techName) {
        // 精确匹配
        if (TECH_REPO_MAPPING.containsKey(techName)) {
            return TECH_REPO_MAPPING.get(techName);
        }

        // 模糊匹配
        for (Map.Entry<String, String> entry : TECH_REPO_MAPPING.entrySet()) {
            if (techName.toLowerCase().contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
            if (entry.getKey().toLowerCase().contains(techName.toLowerCase())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 定时任务：每天凌晨 2 点清理过期缓存并预加载热门仓库数据
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledCacheRefresh() {
        log.info("Running scheduled GitHub trend data refresh...");

        // 清理 24 小时以上的缓存
        long cutoff = System.currentTimeMillis() - 86400000;
        localCache.entrySet().removeIf(entry -> entry.getValue().getTimestamp() < cutoff);

        // 预加载热门仓库
        List<String> hotRepos = Arrays.asList(
                "spring-projects/spring-boot",
                "apache/kafka",
                "redis/redis",
                "elastic/elasticsearch"
        );

        hotRepos.parallelStream().forEach(repo -> {
            try {
                fetchStarHistory(repo);
                log.info("Preloaded trend data for {}", repo);
            } catch (Exception e) {
                log.warn("Failed to preload {}: {}", repo, e.getMessage());
            }
        });

        log.info("GitHub trend data refresh completed.");
    }

    // ========== 内部数据类 ==========

    @Data
    public static class RepoStarsHistory {
        private String repoFullName;
        private Map<LocalDate, Integer> dailyStars;
        private Date lastUpdated;
        private long timestamp;
    }

    @Data
    public static class StarEvent {
        private String user;
        private Instant starredAt;
    }

    @Data
    @lombok.Builder
    public static class StarTrendInfo {
        private String repoFullName;
        private int currentStars;
        private double growthRate;      // 增长率百分比
        private double velocity;        // stars/day
        private TrendCategory trendCategory;
        private Map<LocalDate, Integer> dataPoints;
        private Date lastUpdated;

        public static StarTrendInfo empty(String repoFullName) {
            return StarTrendInfo.builder()
                    .repoFullName(repoFullName)
                    .currentStars(0)
                    .growthRate(0.0)
                    .velocity(0.0)
                    .trendCategory(TrendCategory.UNKNOWN)
                    .dataPoints(Collections.emptyMap())
                    .lastUpdated(new Date())
                    .build();
        }
    }

    public enum TrendCategory {
        VIRAL("爆发增长", "viral"),
        ACCELERATING("加速增长", "accelerating"),
        GROWING("稳定增长", "growing"),
        STABLE("平稳", "stable"),
        DECLINING("下降", "declining"),
        UNKNOWN("未知", "unknown");

        private final String chineseName;
        private final String code;

        TrendCategory(String chineseName, String code) {
            this.chineseName = chineseName;
            this.code = code;
        }

        public String getChineseName() {
            return chineseName;
        }

        public String getCode() {
            return code;
        }
    }
}