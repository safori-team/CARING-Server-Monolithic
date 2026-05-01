package com.caring.infra.ai.gemini;

import com.caring.domain.emotion.entity.EmotionType;

import java.util.Map;
import java.util.Optional;

public final class GeminiEmotionMapping {

    private static final Map<String, EmotionType> CATEGORY_MAP = Map.of(
            "neutral",   EmotionType.NEUTRAL,
            "happy",     EmotionType.HAPPY,
            "sad",       EmotionType.SAD,
            "angry",     EmotionType.ANGRY
    );

    // surprised category 내에서 label로 FEAR vs SURPRISE 구분
    private static final Map<String, EmotionType> SURPRISED_LABEL_MAP = Map.of(
            "fear",              EmotionType.FEAR,
            "anxiety",           EmotionType.FEAR,
            "horror",            EmotionType.FEAR,
            "surprise_positive", EmotionType.SURPRISE,
            "surprise_negative", EmotionType.SURPRISE,
            "awe",               EmotionType.SURPRISE,
            "awkwardness",       EmotionType.SURPRISE
    );

    private GeminiEmotionMapping() {}

    public static Optional<EmotionType> resolve(String label, String category) {
        if ("surprised".equals(category)) {
            return Optional.ofNullable(SURPRISED_LABEL_MAP.get(label));
        }
        return Optional.ofNullable(CATEGORY_MAP.get(category));
    }
}
