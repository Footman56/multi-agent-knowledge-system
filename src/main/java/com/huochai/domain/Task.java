package com.huochai.agent;

import java.util.Date;
import java.util.List;

import lombok.Builder;
import lombok.Data;

// 任务
@Data
@Builder
public class Task {
    private String id;
    private String name;
    private String howToLearn;
    private int estimatedHours;
    private boolean done;
    private Date deadline;
    private List<String> deliverables;
}
