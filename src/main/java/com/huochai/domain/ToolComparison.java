package com.huochai.domain;

import java.util.List;

import lombok.Builder;
import lombok.Data;

// 工具对比
@Data
@Builder
public class ToolComparison {
    private String toolName;
    private List<String> pros;
    private List<String> cons;
    private String bestFor;
}
