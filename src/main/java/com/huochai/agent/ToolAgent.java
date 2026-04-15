package com.huochai.agent;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.huochai.domain.Task;
import com.huochai.domain.ToolLearningResult;
import com.huochai.service.CodeExecutorService;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolAgent extends BaseAgent {

    @Autowired
    @Qualifier("deepseekChatClient")
    private ChatClient deepseekClient;

    @Autowired
    @Qualifier("qwenChatClient")
    private ChatClient qwenClient;

    @Autowired
    private CodeExecutorService codeExecutor;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected String getAgentName() {
        return "ToolAgent";
    }

    @Override
    public Map<String, Object> process(MyAgentState state) throws Exception {
        // 从未完成任务中选取一个工具学习任务
        String toolName = extractToolName(state);
        if (toolName == null) {
            log.info("No tool learning task found, skipping ToolAgent");
            return Map.of("shouldContinue", true);
        }

        log.info("Learning tool: {}", toolName);

        // 1. 使用 Qwen 的 Tool Calling 获取工具基本信息
        ToolBasicInfo basicInfo = fetchToolBasicInfo(toolName);

        // 2. 使用 DeepSeek 生成完整的集成代码和文档
        ToolLearningResult fullResult = generateFullIntegration(toolName, basicInfo);

        // 3. 本地验证代码
        CodeExecutorService.ExecutionResult execResult = codeExecutor.validateAndRun(
                fullResult.getDemoCode(),
                toolName
        );
        fullResult.setExecResult(execResult);

        // 如果代码执行失败，尝试修复一次
        if (!execResult.isSuccess() && state.shouldRetry()) {
            log.warn("Code execution failed, attempting auto-fix...");
            fullResult = attemptAutoFix(toolName, fullResult, execResult);
            state.incrementRetry();
        }

        // 返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("currentLearningTool", toolName);
        result.put("currentToolLearning", fullResult);
        result.put("shouldContinue", true);

        return result;
    }

    private String extractToolName(MyAgentState state) {
        List<Task> unfinished = state.getUnfinishedTasks();
        if (unfinished == null || unfinished.isEmpty()) {
            return null;
        }

        // 查找包含工具名称的任务
        for (Task task : unfinished) {
            String name = task.getName().toLowerCase();
            if (name.contains("redis") || name.contains("kafka") ||
                    name.contains("elasticsearch") || name.contains("rabbitmq") ||
                    name.contains("mysql") || name.contains("mongo")) {
                return extractToolFromName(name);
            }
        }
        return null;
    }

    private String extractToolFromName(String taskName) {
        // 简单映射
        if (taskName.contains("redis")) {
            return "Redis";
        }
        if (taskName.contains("kafka")) {
            return "Kafka";
        }
        if (taskName.contains("elasticsearch")) {
            return "Elasticsearch";
        }
        if (taskName.contains("rabbitmq")) {
            return "RabbitMQ";
        }
        if (taskName.contains("mysql")) {
            return "MySQL";
        }
        if (taskName.contains("mongo")) {
            return "MongoDB";
        }
        return taskName;
    }

    private ToolBasicInfo fetchToolBasicInfo(String toolName) {
        // 使用 Qwen 的 Function Calling 获取工具信息
        // 这里简化实现，实际可调用 Qwen 的内置工具或自定义 Function

        String prompt = """
                请提供关于 %s 的基本信息，包括：
                - 定义和核心特性
                - 常见使用场景
                - Spring Boot 集成所需的 Maven 依赖
                """.formatted(toolName);

        String response = qwenClient.prompt()
                .user(prompt)
                .call()
                .content();

        return parseBasicInfo(response);
    }

    private ToolLearningResult generateFullIntegration(String toolName, ToolBasicInfo basicInfo)
            throws Exception {

        String prompt = """
                你是一位资深 Java 工程师，请为 %s 生成完整的 Spring Boot 集成指南和可运行代码。
                
                基本信息：
                %s
                
                请输出严格的 JSON 格式，包含以下部分：
                {
                  "toolName": "%s",
                  "version": "推荐的最新稳定版本",
                  "concepts": {
                    "definition": "定义",
                    "keyFeatures": ["特性1", "特性2"],
                    "coreComponents": {"组件名": "作用"}
                  },
                  "usage": {
                    "commonScenarios": ["场景1", "场景2"],
                    "bestPractices": ["实践1", "实践2"],
                    "antiPatterns": ["反模式1"]
                  },
                  "integrationSteps": {
                    "prerequisites": ["前置条件"],
                    "steps": [
                      {"order": 1, "description": "步骤描述", "code": "代码片段"}
                    ]
                  },
                  "mavenDependency": "<dependency>...</dependency>",
                  "demoCode": "完整的可运行 Spring Boot 示例代码（包含 Controller, Service, Config）",
                  "productionIssues": [
                    {"problem": "问题", "cause": "原因", "solution": "解决方案", "prevention": "预防措施"}
                  ],
                  "comparisons": [
                    {"toolName": "对比工具", "pros": ["优点"], "cons": ["缺点"], "bestFor": "适用场景"}
                  ],
                  "sourceAnalysis": {
                    "architecture": "架构说明",
                    "keyClasses": ["核心类1", "核心类2"],
                    "designPatterns": "使用的设计模式"
                  }
                }
                
                要求：
                1. demoCode 必须是可直接复制到 Spring Boot 3.5.11 项目中运行的完整代码
                2. 代码需包含必要的注解和配置
                3. 生产问题要真实、有深度
                4. 源码分析要指出关键类和方法
                
                只输出 JSON，不要有其他内容。
                """.formatted(toolName, basicInfo.toString(), toolName);

        String response = deepseekClient.prompt()
                .user(prompt)
                .call()
                .content();

        return objectMapper.readValue(extractJson(response), ToolLearningResult.class);
    }

    private ToolLearningResult attemptAutoFix(String toolName, ToolLearningResult original,
                                              CodeExecutorService.ExecutionResult  error) throws Exception {
        String fixPrompt = """
                以下 %s 的 Spring Boot 集成代码执行失败：
                
                错误信息：%s
                
                原代码：
                %s
                
                请修复代码并返回完整的修正后的 JSON（格式与之前相同）。
                只输出 JSON。
                """.formatted(toolName, error.getErrorMessage(), original.getDemoCode());

        String response = deepseekClient.prompt()
                .user(fixPrompt)
                .call()
                .content();

        return objectMapper.readValue(extractJson(response), ToolLearningResult.class);
    }

    private String extractJson(String response) {
        if (response.contains("```json")) {
            return response.substring(response.indexOf("```json") + 7,
                    response.lastIndexOf("```")).trim();
        } else if (response.contains("```")) {
            return response.substring(response.indexOf("```") + 3,
                    response.lastIndexOf("```")).trim();
        }
        return response.trim();
    }

    private ToolBasicInfo parseBasicInfo(String response) {
        // 简化实现
        ToolBasicInfo info = new ToolBasicInfo();
        info.setRawContent(response);
        return info;
    }

    @lombok.Data
    static class ToolBasicInfo {
        private String rawContent;
    }

    @lombok.Data
    static class ExecutionResult {
        private boolean success;
        private String errorMessage;
        private String output;
    }
}