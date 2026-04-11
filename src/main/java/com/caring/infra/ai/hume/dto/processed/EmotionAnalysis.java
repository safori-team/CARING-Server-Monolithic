package com.caring.infra.ai.hume.dto.processed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class EmotionAnalysis {
    private final String source = "hume";
    private ProsodyResult prosody;
    private BurstResult burst;
    private LanguageResult language;
}
