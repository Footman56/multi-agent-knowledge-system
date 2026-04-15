package com.huochai.agent;

import java.util.Date;
import java.util.List;

import lombok.Builder;
import lombok.Data;

// 学习计划
@Data
@Builder
public class LearningPlan {
    private Integer week;
    private List<String> topics;
    private List<Task> tasks;
    private boolean rollover;
    private Date createdAt;
    private Date completedAt;
}
