package com.caring.infra.ai.hume.dto.callback;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class HumeGroupedPrediction {
    private String id;
    private List<HumePrediction> predictions;
}
