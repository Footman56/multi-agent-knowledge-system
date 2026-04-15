package com.huochai.agent;


import com.huochai.domain.LearningPlan;
import com.huochai.domain.Skill;
import com.huochai.domain.Task;
import com.huochai.domain.TokenStats;
import com.huochai.domain.ToolLearningResult;
import com.huochai.domain.TrendAnalysis;

import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MyAgentState extends AgentState {
    // 用户输入
    private String userId;
    private String userRequest;
    private List<String> targetTech;
    private String userLevel; // beginner, intermediate, advanced

    // ArchitectAgent 输出
    private List<Skill> skills;
    private TrendAnalysis trendAnalysis;
    private String priorityLevel;

    // PlannerAgent 输出
    private Integer currentWeek;
    private List<LearningPlan> learningPlans;
    private List<Task> unfinishedTasks;

    // ToolAgent 输出
    private String currentLearningTool;
    private ToolLearningResult currentToolLearning;

    // DocAgent 输出
    private String markdownPreview;
    private String previewId;
    private boolean userConfirmed;

    // KnowledgeAgent 输出
    private String knowledgePath;
    private boolean duplicateDetected;
    private String mergedContent;

    // OptimizerAgent 输出
    private Map<String, Object> compressedContext;
    private TokenStats tokenStats;

    // 元数据
    private Map<String, Object> metadata;

    // 执行控制
    private boolean shouldContinue;
    private String errorMessage;
    private int retryCount;

    // 辅助方法
    public boolean hasUnfinishedTasks() {
        return unfinishedTasks != null && !unfinishedTasks.isEmpty();
    }

    public void incrementRetry() {
        this.retryCount++;
    }

    public boolean shouldRetry() {
        return retryCount < 3;
    }
}

