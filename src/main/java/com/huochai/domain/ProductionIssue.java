package com.huochai.agent;

import lombok.Builder;
import lombok.Data;

// 生产问题
@Data
@Builder
public class ProductionIssue {
    private String problem;
    private String cause;
    private String solution;
    private String prevention;
}
