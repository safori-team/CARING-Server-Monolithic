package com.caring.infra.ai.hume.mapper;

import com.caring.domain.emotion.entity.EmotionType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hume AI 48/53개 세부 감정 → 6개 EmotionType 카테고리 매핑.
 * 매핑 변경 시 이 파일만 수정하면 된다.
 */
public final class HumeEmotionMapping {

    private HumeEmotionMapping() {}

    public static final Map<String, EmotionType> EMOTION_MAP;

    static {
        Map<String, EmotionType> map = new LinkedHashMap<>();

        // HAPPY
        map.put("Joy", EmotionType.HAPPY);
        map.put("Amusement", EmotionType.HAPPY);
        map.put("Contentment", EmotionType.HAPPY);
        map.put("Ecstasy", EmotionType.HAPPY);
        map.put("Excitement", EmotionType.HAPPY);
        map.put("Love", EmotionType.HAPPY);
        map.put("Pride", EmotionType.HAPPY);
        map.put("Satisfaction", EmotionType.HAPPY);
        map.put("Triumph", EmotionType.HAPPY);
        map.put("Relief", EmotionType.HAPPY);
        map.put("Adoration", EmotionType.HAPPY);
        map.put("Gratitude", EmotionType.HAPPY);       // language only
        map.put("Enthusiasm", EmotionType.HAPPY);       // language only

        // SAD
        map.put("Sadness", EmotionType.SAD);
        map.put("Disappointment", EmotionType.SAD);
        map.put("Distress", EmotionType.SAD);
        map.put("Empathic Pain", EmotionType.SAD);
        map.put("Nostalgia", EmotionType.SAD);
        map.put("Guilt", EmotionType.SAD);
        map.put("Shame", EmotionType.SAD);
        map.put("Tiredness", EmotionType.SAD);

        // ANGRY
        map.put("Anger", EmotionType.ANGRY);
        map.put("Contempt", EmotionType.ANGRY);
        map.put("Disgust", EmotionType.ANGRY);
        map.put("Envy", EmotionType.ANGRY);
        map.put("Annoyance", EmotionType.ANGRY);        // language only
        map.put("Disapproval", EmotionType.ANGRY);      // language only

        // FEAR
        map.put("Fear", EmotionType.FEAR);
        map.put("Anxiety", EmotionType.FEAR);
        map.put("Horror", EmotionType.FEAR);
        map.put("Doubt", EmotionType.FEAR);

        // SURPRISE
        map.put("Surprise (positive)", EmotionType.SURPRISE);
        map.put("Surprise (negative)", EmotionType.SURPRISE);
        map.put("Awe", EmotionType.SURPRISE);
        map.put("Realization", EmotionType.SURPRISE);

        // NEUTRAL — 명시적으로 등록 (나머지는 fallback)
        map.put("Calmness", EmotionType.NEUTRAL);
        map.put("Concentration", EmotionType.NEUTRAL);
        map.put("Contemplation", EmotionType.NEUTRAL);
        map.put("Boredom", EmotionType.NEUTRAL);
        map.put("Interest", EmotionType.NEUTRAL);
        map.put("Aesthetic Appreciation", EmotionType.NEUTRAL);
        map.put("Admiration", EmotionType.NEUTRAL);
        map.put("Awkwardness", EmotionType.NEUTRAL);
        map.put("Confusion", EmotionType.NEUTRAL);
        map.put("Craving", EmotionType.NEUTRAL);
        map.put("Desire", EmotionType.NEUTRAL);
        map.put("Determination", EmotionType.NEUTRAL);
        map.put("Embarrassment", EmotionType.NEUTRAL);
        map.put("Entrancement", EmotionType.NEUTRAL);
        map.put("Pain", EmotionType.NEUTRAL);
        map.put("Romance", EmotionType.NEUTRAL);
        map.put("Sympathy", EmotionType.NEUTRAL);
        map.put("Sarcasm", EmotionType.NEUTRAL);         // language only

        EMOTION_MAP = Collections.unmodifiableMap(map);
    }

    /**
     * Hume 감정명을 EmotionType으로 변환. 매핑에 없으면 NEUTRAL.
     */
    public static EmotionType resolve(String humeEmotionName) {
        return EMOTION_MAP.getOrDefault(humeEmotionName, EmotionType.NEUTRAL);
    }
}
