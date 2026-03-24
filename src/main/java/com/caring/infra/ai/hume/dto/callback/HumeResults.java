package com.caring.infra.ai.hume.dto.callback;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class HumeResults {
    private List<HumeInferencePrediction> predictions;
    private List<HumeError> errors;

    @Getter
    @NoArgsConstructor
    public static class HumeError {
        private String message;
        private String file;
    }
}
