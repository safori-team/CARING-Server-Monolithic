package com.caring.infra.ai.hume.dto.callback;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HumeEmotion {
    private String name;
    private double score;
}
