package com.caring.infra.ai.hume.dto.processed;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EmotionScore {
    private String name;
    private double score;
}
