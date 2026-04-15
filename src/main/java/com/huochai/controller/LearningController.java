package com.huochai.controller;


import com.huochai.agent.MyAgentState;
import com.huochai.service.PreviewService;

import org.bsc.langgraph4j.StateGraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learn")
public class LearningController {

    @Autowired
    private StateGraph<MyAgentState> learningGraph;

    @Autowired
    private PreviewService previewService;

    @PostMapping("/start")
    public Map<String, Object> startLearning(@RequestBody LearningRequest request) {
        // 初始化状态
        MyAgentState initialState = MyAgentState.builder()
                .userId(request.getUserId())
                .userRequest(request.getTargetTech())
                .targetTech(request.getTargetTechList())
                .userLevel(request.getLevel())
                .currentWeek(0)
                .metadata(new HashMap<>())
                .shouldContinue(true)
                .retryCount(0)
                .build();

        // 执行图
        MyAgentState finalState = learningGraph.invoke(initialState);

        return Map.of(
                "status", "success",
                "currentWeek", finalState.getCurrentWeek(),
                "learningPlans", finalState.getLearningPlans(),
                "previewId", finalState.getPreviewId()
        );
    }

    @GetMapping("/progress/{userId}")
    public Map<String, Object> getProgress(@PathVariable String userId) {
        // 从数据库获取进度
        return Map.of("userId", userId, "progress", "implement me");
    }

    @PostMapping("/doc/confirm")
    public Map<String, Object> confirmDocument(@RequestBody ConfirmRequest request) {
        previewService.confirmPreview(request.getPreviewId());

        // 在实际实现中，需要通过某种机制将确认信号传递给正在等待的 DocAgent
        // 这里简化返回

        return Map.of("status", "confirmed", "previewId", request.getPreviewId());
    }

    @GetMapping("/doc/preview/{previewId}")
    public Map<String, Object> getPreview(@PathVariable String previewId) {
        String content = previewService.getPreview(previewId);
        return Map.of("previewId", previewId, "content", content);
    }

    // 请求体类
    @lombok.Data
    static class LearningRequest {
        private String userId;
        private String targetTech;
        private String level;

        public List<String> getTargetTechList() {
            return Arrays.asList(targetTech.split(","));
        }
    }

    @lombok.Data
    static class ConfirmRequest {
        private String previewId;
        private boolean confirmed;
    }
}