package com.huochai.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Step {
    private int order;
    private String description;
    private String code;
}
