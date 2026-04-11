package com.caring.infra.ai.hume.dto.processed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ProsodyResult {
    private List<EmotionScore> summary;
    private List<ProsodyUtterance> utterances;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class ProsodyUtterance {
        private String text;
        private TimeRange time;
        private double confidence;
        private List<EmotionScore> topEmotions;
    }

    @Getter
    @AllArgsConstructor
    public static class TimeRange {
        private double begin;
        private double end;
    }
}
