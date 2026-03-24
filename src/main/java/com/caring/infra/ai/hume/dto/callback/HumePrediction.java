package com.caring.infra.ai.hume.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class HumePrediction {
    private String text;
    private HumeTimeRange time;
    private double confidence;
    @JsonProperty("speaker_confidence")
    private Double speakerConfidence;
    private List<HumeEmotion> emotions;
    private List<HumeDescription> descriptions;
    private HumeTextPosition position;
    private List<HumeEmotion> sentiment;
    private List<HumeEmotion> toxicity;
}
