package com.huochai.agent;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.huochai.domain.Skill;
import com.huochai.domain.TokenStats;
import com.huochai.domain.TrendAnalysis;
import com.huochai.service.GitHubTrendService;

import org.bsc.langgraph4j.state.AgentState;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ArchitectAgent extends BaseAgent {

    @Autowired
    @Qualifier("deepseekChatClient")
    private ChatClient deepseekClient;

    @Autowired
    private GitHubTrendService gitHubTrendService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected String getAgentName() {
        return "ArchitectAgent";
    }

    @Override
    public Map<String, Object> process(MyAgentState state) throws Exception {
        List<String> targetTech = state.getTargetTech();
        if (targetTech == null || targetTech.isEmpty()) {
            targetTech = getDefaultTechStack();
        }

        // 1. 获取 GitHub 趋势数据（本地处理，节省 Token）
        Map<String, Double> githubTrends = gitHubTrendService.getStarGrowthTrends(targetTech);

        // 2. 调用 DeepSeek 进行能力模型构建和趋势分析
        String prompt = buildArchitectPrompt(targetTech, githubTrends, state.getUserLevel());

        String response = deepseekClient.prompt()
                .user(prompt)
                .call()
                .content();

        // 3. 解析 JSON 响应
        ArchitectResponse archResponse = parseResponse(response);

        // 4. 补充 GitHub 数据到趋势分析
        archResponse.getTrendAnalysis().setGithubStarGrowth(githubTrends);

        // 5. 计算优先级
        String priority = calculatePriority(archResponse);

        // 6. 返回状态更新
        Map<String, Object> result = new HashMap<>();
        result.put("skills", archResponse.getSkills());
        result.put("trendAnalysis", archResponse.getTrendAnalysis());
        result.put("priorityLevel", priority);
        result.put("shouldContinue", true);

        // 记录 Token 使用（实际可通过 ChatClient 的回调获取）
        recordTokenUsage(state, response);

        return result;
    }

    private String buildArchitectPrompt(List<String> techs, Map<String, Double> trends, String level) {
        return """
                你是一位资深 Java 架构师，请根据以下信息构建高级 Java 工程师能力模型：
                
                目标技术栈：%s
                GitHub Star 增长趋势：%s
                学习者水平：%s
                
                请输出严格的 JSON 格式，包含：
                1. skills: 技能列表，每个技能包含 name, category, importance(high/medium/low), 
                   timeEstimateHours, prerequisites, learningResources
                2. trendAnalysis: 包含 lifecycleStage(emerging/growing/mature/declining), 
                   jobMarketDemand(1-10分), recommendations
                
                注意：importance 需结合 GitHub 趋势和企业实际需求综合判断。
                
                只输出 JSON，不要有其他内容。
                """.formatted(
                String.join(", ", techs),
                trends.toString(),
                level != null ? level : "intermediate"
        );
    }

    private ArchitectResponse parseResponse(String response) throws Exception {
        // 提取 JSON 部分（DeepSeek 有时会包裹 markdown 代码块）
        String json = response;
        if (response.contains("```json")) {
            json = response.substring(response.indexOf("```json") + 7);
            json = json.substring(0, json.indexOf("```"));
        } else if (response.contains("```")) {
            json = response.substring(response.indexOf("```") + 3);
            json = json.substring(0, json.indexOf("```"));
        }

        return objectMapper.readValue(json, ArchitectResponse.class);
    }

    private String calculatePriority(ArchitectResponse response) {
        // 根据技能的重要性和趋势判断整体优先级
        long highCount = response.getSkills().stream()
                .filter(s -> "high".equals(s.getImportance()))
                .count();

        if (highCount > response.getSkills().size() / 2) {
            return "high";
        } else if (highCount > 0) {
            return "medium";
        }
        return "low";
    }

    private void recordTokenUsage(MyAgentState state, String response) {
        // 简化实现，实际可通过 ChatClient 的 metrics 获取
        TokenStats stats = new TokenStats();
        stats.setPromptTokens(estimateTokens(buildArchitectPrompt(state.getTargetTech(),
                Collections.emptyMap(), state.getUserLevel())));
        stats.setCompletionTokens(estimateTokens(response));
        stats.setModel("deepseek-chat");

        state.setTokenStats(stats);
    }

    private int estimateTokens(String text) {
        // 粗略估算：中文约 1.5 字符/token，英文约 4 字符/token
        return text.length() / 3;
    }

    private List<String> getDefaultTechStack() {
        return Arrays.asList(
                "Spring Boot", "JVM", "Redis", "Kafka", "MySQL",
                "Microservices", "Docker", "Kubernetes", "RAG"
        );
    }

    public Map<String, Object> process(AgentState agentState) {
        return process((MyAgentState) agentState);
    }

    // 内部响应类
    @lombok.Data
    static class ArchitectResponse {
        private List<Skill> skills;
        private TrendAnalysis trendAnalysis;
    }
}