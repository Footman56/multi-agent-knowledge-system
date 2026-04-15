package com.huochai.domain;

import lombok.Data;

// Token 统计
@Data
public class TokenStats {
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private double cost;
    private String model;

    public void add(TokenStats other) {
        this.promptTokens += other.promptTokens;
        this.completionTokens += other.completionTokens;
        this.totalTokens += other.totalTokens;
        this.cost += other.cost;
    }
}
