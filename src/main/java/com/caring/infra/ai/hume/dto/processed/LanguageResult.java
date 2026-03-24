package com.caring.infra.ai.hume.dto.processed;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class LanguageResult {
    private List<EmotionScore> summary;
    private SentimentResult sentiment;
    private List<EmotionScore> toxicity;
    private List<LanguageUtterance> utterances;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class SentimentResult {
        private List<EmotionScore> distribution;
        private int dominant;
        private double weightedMean;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class LanguageUtterance {
        private String text;
        private TextPosition position;
        private List<EmotionScore> topEmotions;
        private int sentimentDominant;
    }

    @Getter
    @AllArgsConstructor
    public static class TextPosition {
        private int begin;
        private int end;
    }
}
