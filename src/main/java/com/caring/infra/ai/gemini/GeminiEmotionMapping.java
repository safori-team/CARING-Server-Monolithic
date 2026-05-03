package com.caring.infra.ai.gemini;

import com.caring.domain.emotion.entity.EmotionType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class GeminiEmotionMapping {

    // 6대 감정 카테고리 → EmotionType 직접 매핑
    // HashMap 사용 — null 키에도 안전 (Map.of()는 null 키로 NPE 던짐)
    private static final Map<String, EmotionType> CATEGORY_MAP = new HashMap<>();

    static {
        CATEGORY_MAP.put("neutral",  EmotionType.NEUTRAL);
        CATEGORY_MAP.put("happy",    EmotionType.HAPPY);
        CATEGORY_MAP.put("sad",      EmotionType.SAD);
        CATEGORY_MAP.put("angry",    EmotionType.ANGRY);
        CATEGORY_MAP.put("fear",     EmotionType.FEAR);
        CATEGORY_MAP.put("surprise", EmotionType.SURPRISE);
    }

    private GeminiEmotionMapping() {}

    /**
     * Gemini segment의 category 값을 EmotionType으로 변환.
     * label(세부 감정명)은 현재 category 매핑에는 사용되지 않지만
     * 추후 세부 감정 저장 확장을 고려해 파라미터로 유지.
     */
    public static Optional<EmotionType> resolve(String label, String category) {
        if (category == null) return Optional.empty();
        return Optional.ofNullable(CATEGORY_MAP.get(category.toLowerCase()));
    }
}
