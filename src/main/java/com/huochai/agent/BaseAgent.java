package com.huochai.agent;


import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public abstract class BaseAgent implements NodeAction<MyAgentState> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public Map<String, Object> apply(MyAgentState state) {
        long startTime = System.currentTimeMillis();
        log.info("Agent [{}] started", getAgentName());

        try {
            // 前置处理：Token 统计、上下文压缩等
            beforeProcess(state);

            // 核心处理逻辑
            Map<String, Object> result = process(state);

            // 后置处理：更新状态、记录日志
            afterProcess(state, result);

            log.info("Agent [{}] completed in {}ms",
                    getAgentName(), System.currentTimeMillis() - startTime);

            return result;
        } catch (Exception e) {
            log.error("Agent [{}] failed: {}", getAgentName(), e.getMessage(), e);
            return handleError(state, e);
        }
    }

    protected void beforeProcess(MyAgentState state) {
        // 子类可覆盖
    }

    protected abstract Map<String, Object> process(MyAgentState state) throws Exception;

    protected void afterProcess(MyAgentState state, Map<String, Object> result) {
        // 子类可覆盖
    }

    protected Map<String, Object> handleError(MyAgentState state, Exception e) {
        Map<String, Object> errorResult = new HashMap<>();
        errorResult.put("errorMessage", e.getMessage());
        errorResult.put("shouldContinue", false);
        return errorResult;
    }

    protected abstract String getAgentName();

    // 工具方法：合并结果到返回 Map
    protected Map<String, Object> resultMap(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
}