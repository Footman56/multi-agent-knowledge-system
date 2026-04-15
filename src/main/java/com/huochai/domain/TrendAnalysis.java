package com.huochai.agent;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

// 趋势分析
@Data
@Builder
public class TrendAnalysis {
    private Map<String, Double> githubStarGrowth; // 技术 -> 增长率
    private Map<String, Integer> jobMarketDemand;  // 技术 -> 需求量
    private String lifecycleStage;                 // emerging, growing, mature, declining
    private List<String> recommendations;
}
