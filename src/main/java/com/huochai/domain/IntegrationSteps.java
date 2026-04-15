package com.huochai.domain;

import java.util.List;

import lombok.Builder;
import lombok.Data;

// 集成步骤
@Data
@Builder
public class IntegrationSteps {
    private List<String> prerequisites;
    private List<Step> steps;
}
