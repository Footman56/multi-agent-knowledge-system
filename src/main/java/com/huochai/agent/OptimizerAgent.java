package com.huochai.agent;


import com.huochai.domain.TokenStats;
import com.huochai.service.TokenOptimizationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class OptimizerAgent extends BaseAgent {

    @Autowired
    private TokenOptimizationService tokenService;

    @Override
    protected String getAgentName() {
        return "OptimizerAgent";
    }

    @Override
    public Map<String, Object> process(MyAgentState state) throws Exception {
        // 1. 统计 Token 使用
        TokenStats stats = state.getTokenStats();
        if (stats != null) {
            tokenService.recordUsage(getAgentName(), stats);
        }

        // 2. 压缩上下文（为下一轮循环准备）
        Map<String, Object> compressed = new HashMap<>();

        // 对长文本内容进行本地存储，只保留引用
        if (state.getCurrentToolLearning() != null) {
            String demoCode = state.getCurrentToolLearning().getDemoCode();
            if (demoCode != null && demoCode.length() > 2000) {
                String chunkId = tokenService.storeLongText(demoCode);
                compressed.put("demoCodeRef", chunkId);
                // 清除原始内容，减少状态大小
                state.getCurrentToolLearning().setDemoCode("[stored in vector db: " + chunkId + "]");
            }
        }

        // 3. 判断是否需要继续循环
        boolean shouldContinue = state.hasUnfinishedTasks() &&
                !hasReachedMaxWeeks(state);

        // 4. 如果继续循环，清理中间状态以减少内存
        if (shouldContinue) {
            // 清理已完成的临时数据
            state.setCurrentToolLearning(null);
            state.setMarkdownPreview(null);
            state.setUserConfirmed(false);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("compressedContext", compressed);
        result.put("shouldContinue", shouldContinue);

        // 记录优化日志
        log.info("Token optimization completed. Continue: {}, Compressed fields: {}",
                shouldContinue, compressed.size());

        return result;
    }

    private boolean hasReachedMaxWeeks(MyAgentState state) {
        // 最多学习 12 周
        return state.getCurrentWeek() != null && state.getCurrentWeek() >= 12;
    }
}