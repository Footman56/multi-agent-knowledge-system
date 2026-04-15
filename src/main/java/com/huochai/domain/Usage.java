package com.huochai.domain;

import java.util.List;

import lombok.Builder;
import lombok.Data;

// 使用方式
@Data
@Builder
public class Usage {
    private List<String> commonScenarios;
    private List<String> bestPractices;
    private List<String> antiPatterns;
}
