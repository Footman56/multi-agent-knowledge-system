package com.huochai.domain;

import java.util.List;

import lombok.Builder;
import lombok.Data;

// 技能模型
@Data
@Builder
public class Skill {
    private String name;
    private String category;
    private String importance; // high, medium, low
    private int timeEstimateHours;
    private List<String> prerequisites;
    private List<String> learningResources;
}
