package com.huochai.agent;

import java.util.List;

import lombok.Builder;
import lombok.Data;

// 工具学习结果
@Data
@Builder
public class ToolLearningResult {
    private String toolName;
    private String version;
    private Concepts concepts;
    private Usage usage;
    private IntegrationSteps integrationSteps;
    private String mavenDependency;
    private String demoCode;
    private List<ProductionIssue> productionIssues;
    private List<ToolComparison> comparisons;
    private SourceCodeAnalysis sourceAnalysis;
}
