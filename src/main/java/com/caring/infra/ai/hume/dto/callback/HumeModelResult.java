package com.caring.infra.ai.hume.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor
public class HumeModelResult {
    private Map<String, Object> metadata;
    @JsonProperty("grouped_predictions")
    private List<HumeGroupedPrediction> groupedPredictions;
}
