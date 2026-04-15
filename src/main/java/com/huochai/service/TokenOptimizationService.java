package com.huochai.service;


import com.huochai.domain.TokenStats;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class TokenOptimizationService {

    private static final Logger log = LoggerFactory.getLogger(TokenOptimizationService.class);

    @Autowired
    private VectorStoreService vectorStore;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final Map<String, TokenStats> dailyStats = new ConcurrentHashMap<>();

    public void recordUsage(String agent, TokenStats stats) {
        dailyStats.merge(agent, stats, (old, newStats) -> {
            old.add(newStats);
            return old;
        });

        // 持久化到 Redis 用于监控
        String key = "token:usage:" + java.time.LocalDate.now();
        redisTemplate.opsForHash().increment(key, agent + ":prompt", stats.getPromptTokens());
        redisTemplate.opsForHash().increment(key, agent + ":completion", stats.getCompletionTokens());
        redisTemplate.expire(key, 7, TimeUnit.DAYS);

        log.debug("Token usage - {}: prompt={}, completion={}, cost=${}",
                agent, stats.getPromptTokens(), stats.getCompletionTokens(),
                String.format("%.4f", stats.getCost()));
    }

    public String storeLongText(String text) {
        return vectorStore.storeLongText(text);
    }

    public String compressContext(String context) {
        // 简单压缩：移除多余空白、注释等
        return context.replaceAll("\\s+", " ")
                .replaceAll("/\\*.*?\\*/", "")
                .replaceAll("//.*?\\n", "\n");
    }

    public Map<String, TokenStats> getDailyStats() {
        return Map.copyOf(dailyStats);
    }
}