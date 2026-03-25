package com.caring.infra.ai.hume.dto.processed;

import com.caring.domain.emotion.entity.EmotionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class EmotionCategoryResult {
    private final Map<EmotionType, Integer> emotionBps;
    private final EmotionType topEmotion;
    private final int topEmotionConfidenceBps;
}
