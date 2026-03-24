package com.caring.infra.ai.hume.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HumeInferencePrediction {
    private String file;
    @JsonProperty("file_type")
    private String fileType;
    private HumeModels models;
}
