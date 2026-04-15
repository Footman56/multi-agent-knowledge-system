package com.huochai.service;


import com.huochai.domain.ProductionIssue;
import com.huochai.domain.Step;
import com.huochai.domain.ToolComparison;
import com.huochai.domain.ToolLearningResult;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Service
public class MarkdownGeneratorService {

    public String generate(ToolLearningResult result) {
        StringBuilder md = new StringBuilder();

        // 标题
        md.append("# ").append(result.getToolName()).append(" 实战指南\n\n");
        md.append("> 生成时间：").append(LocalDate.now().format(DateTimeFormatter.ISO_DATE)).append("\n");
        md.append("> 版本：").append(result.getVersion()).append("\n\n");

        // 目录
        md.append("## 目录\n\n");
        md.append("- [背景](#背景)\n");
        md.append("- [核心概念](#核心概念)\n");
        md.append("- [实战步骤](#实战步骤)\n");
        md.append("- [源码分析](#源码分析)\n");
        md.append("- [生产问题](#生产问题)\n");
        md.append("- [工具对比](#工具对比)\n\n");

        // 背景
        md.append("## 背景\n\n");
        md.append(result.getConcepts().getDefinition()).append("\n\n");
        md.append("### 核心特性\n\n");
        for (String feature : result.getConcepts().getKeyFeatures()) {
            md.append("- ").append(feature).append("\n");
        }
        md.append("\n");

        // 核心概念
        md.append("## 核心概念\n\n");
        result.getConcepts().getCoreComponents().forEach((name, desc) -> {
            md.append("### ").append(name).append("\n\n");
            md.append(desc).append("\n\n");
        });

        // 实战步骤
        md.append("## 实战步骤\n\n");
        md.append("### 前置条件\n\n");
        for (String pre : result.getIntegrationSteps().getPrerequisites()) {
            md.append("- ").append(pre).append("\n");
        }
        md.append("\n### Maven 依赖\n\n");
        md.append("```xml\n");
        md.append(result.getMavenDependency()).append("\n");
        md.append("```\n\n");

        md.append("### 集成步骤\n\n");
        for (Step step : result.getIntegrationSteps().getSteps()) {
            md.append("#### ").append(step.getOrder()).append(". ").append(step.getDescription()).append("\n\n");
            if (step.getCode() != null && !step.getCode().isEmpty()) {
                md.append("```java\n");
                md.append(step.getCode()).append("\n");
                md.append("```\n\n");
            }
        }

        // 完整示例代码
        md.append("### 完整示例代码\n\n");
        md.append("```java\n");
        md.append(result.getDemoCode()).append("\n");
        md.append("```\n\n");

        // 使用方式
        md.append("### 使用建议\n\n");
        md.append("#### 常见场景\n\n");
        for (String scenario : result.getUsage().getCommonScenarios()) {
            md.append("- ").append(scenario).append("\n");
        }
        md.append("\n#### 最佳实践\n\n");
        for (String practice : result.getUsage().getBestPractices()) {
            md.append("- ").append(practice).append("\n");
        }
        md.append("\n#### 避免的反模式\n\n");
        for (String anti : result.getUsage().getAntiPatterns()) {
            md.append("- ").append(anti).append("\n");
        }
        md.append("\n");

        // 源码分析
        md.append("## 源码分析\n\n");
        md.append("### 架构概述\n\n");
        md.append(result.getSourceAnalysis().getArchitecture()).append("\n\n");
        md.append("### 核心类\n\n");
        for (String cls : result.getSourceAnalysis().getKeyClasses()) {
            md.append("- `").append(cls).append("`\n");
        }
        md.append("\n### 设计模式\n\n");
        md.append(result.getSourceAnalysis().getDesignPatterns()).append("\n\n");

        // 生产问题
        md.append("## 生产问题\n\n");
        int index = 1;
        for (ProductionIssue issue : result.getProductionIssues()) {
            md.append("### 问题 ").append(index++).append("：").append(issue.getProblem()).append("\n\n");
            md.append("**原因**：").append(issue.getCause()).append("\n\n");
            md.append("**解决方案**：").append(issue.getSolution()).append("\n\n");
            md.append("**预防措施**：").append(issue.getPrevention()).append("\n\n");
        }

        // 工具对比
        md.append("## 同类工具对比\n\n");
        md.append("| 工具 | 优点 | 缺点 | 适用场景 |\n");
        md.append("|------|------|------|----------|\n");
        for (ToolComparison comp : result.getComparisons()) {
            md.append("| ").append(comp.getToolName()).append(" | ");
            md.append(String.join("<br>", comp.getPros())).append(" | ");
            md.append(String.join("<br>", comp.getCons())).append(" | ");
            md.append(comp.getBestFor()).append(" |\n");
        }
        md.append("\n");

        return md.toString();
    }
}