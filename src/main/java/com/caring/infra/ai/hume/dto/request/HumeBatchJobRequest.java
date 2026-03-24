package com.caring.infra.ai.hume.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class HumeBatchJobRequest {
    private List<String> urls;
    @JsonProperty("callback_url")
    private String callbackUrl;
    private Map<String, Object> models;
    private Map<String, Object> transcription;

    public static HumeBatchJobRequest of(List<String> urls, String callbackUrl) {
        return HumeBatchJobRequest.builder()
                .urls(urls)
                .callbackUrl(callbackUrl)
                .models(Map.of(
                        "prosody", Map.of("identify_speakers", false),
                        "burst", Map.of(),
                        "language", Map.of(
                                "identify_speakers", false,
                                "sentiment", Map.of(),
                                "toxicity", Map.of()
                        )
                ))
                .transcription(Map.of("language", "ko"))
                .build();
    }
}
