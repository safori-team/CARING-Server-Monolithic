package com.caring.infra.ai.hume.dto.processed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class BurstResult {
    private List<EmotionScore> summary;
    private List<BurstEvent> events;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class BurstEvent {
        private ProsodyResult.TimeRange time;
        private String description;
        private List<EmotionScore> topEmotions;
    }
}
