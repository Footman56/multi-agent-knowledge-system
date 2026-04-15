package com.huochai.domain;

import java.util.List;

import lombok.Builder;
import lombok.Data;

// 源码分析
@Data
@Builder
public class SourceCodeAnalysis {
    private String architecture;
    private List<String> keyClasses;
    private String designPatterns;
}
