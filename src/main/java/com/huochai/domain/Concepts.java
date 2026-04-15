package com.huochai.domain;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

// 概念
@Data
@Builder
public class Concepts {
    private String definition;
    private List<String> keyFeatures;
    private Map<String, String> coreComponents;
}
