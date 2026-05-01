package com.caring.infra.ai.gemini;

import com.caring.domain.emotion.entity.EmotionType;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.infra.ai.gemini.dto.GeminiAnalysisResult;
import com.caring.infra.ai.gemini.dto.GeminiEmotionScore;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class GeminiEmotionMapper {

    // Caring-Voice va_fusion.py의 EMOTION_VA 앵커값
    private static final Map<EmotionType, double[]> VA_ANCHOR = new EnumMap<>(EmotionType.class);

    static {
        VA_ANCHOR.put(EmotionType.HAPPY,    new double[]{+0.80, +0.60});
        VA_ANCHOR.put(EmotionType.SAD,      new double[]{-0.70, -0.40});
        VA_ANCHOR.put(EmotionType.NEUTRAL,  new double[]{ 0.00,  0.00});
        VA_ANCHOR.put(EmotionType.ANGRY,    new double[]{-0.70, +0.80});
        VA_ANCHOR.put(EmotionType.FEAR,     new double[]{-0.60, +0.70});
        VA_ANCHOR.put(EmotionType.SURPRISE, new double[]{ 0.00, +0.85});
    }

    public VoiceComposite toVoiceComposite(GeminiAnalysisResult result, Voice voice) {
        Map<EmotionType, Double> intensitySum = aggregateIntensity(result);
        Map<EmotionType, Integer> bps = toBps(intensitySum);

        EmotionType topEmotion = bps.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(EmotionType.NEUTRAL);

        int[] va = computeVA(bps);

        return VoiceComposite.builder()
                .voice(voice)
                .happyBps(bps.getOrDefault(EmotionType.HAPPY, 0))
                .sadBps(bps.getOrDefault(EmotionType.SAD, 0))
                .neutralBps(bps.getOrDefault(EmotionType.NEUTRAL, 0))
                .angryBps(bps.getOrDefault(EmotionType.ANGRY, 0))
                .fearBps(bps.getOrDefault(EmotionType.FEAR, 0))
                .surpriseBps(bps.getOrDefault(EmotionType.SURPRISE, 0))
                .topEmotion(topEmotion)
                .topEmotionConfidenceBps(bps.getOrDefault(topEmotion, 0))
                .valenceX1000(va[0])
                .arousalX1000(va[1])
                .intensityX1000(va[2])
                .build();
    }

    private Map<EmotionType, Double> aggregateIntensity(GeminiAnalysisResult result) {
        Map<EmotionType, Double> sum = new EnumMap<>(EmotionType.class);
        for (var segment : result.segments()) {
            for (GeminiEmotionScore score : segment.emotions()) {
                GeminiEmotionMapping.resolve(score.label(), score.category())
                        .ifPresent(type -> sum.merge(type, score.intensity(), Double::sum));
            }
        }
        return sum;
    }

    private Map<EmotionType, Integer> toBps(Map<EmotionType, Double> intensitySum) {
        double total = intensitySum.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0) {
            Map<EmotionType, Integer> fallback = new EnumMap<>(EmotionType.class);
            for (EmotionType t : EmotionType.values()) {
                fallback.put(t, 0);
            }
            fallback.put(EmotionType.NEUTRAL, 10000);
            return fallback;
        }

        Map<EmotionType, Integer> bps = new EnumMap<>(EmotionType.class);
        int assigned = 0;
        EmotionType maxType = EmotionType.NEUTRAL;
        int maxVal = -1;

        for (EmotionType type : EmotionType.values()) {
            double intensity = intensitySum.getOrDefault(type, 0.0);
            int val = (int) Math.round(intensity / total * 10000);
            bps.put(type, val);
            assigned += val;
            if (val > maxVal) {
                maxVal = val;
                maxType = type;
            }
        }

        // 반올림 오차 보정 (합계 = 10000 보증)
        int diff = 10000 - assigned;
        bps.put(maxType, bps.get(maxType) + diff);
        return bps;
    }

    private int[] computeVA(Map<EmotionType, Integer> bps) {
        double v = 0, a = 0;
        for (EmotionType type : EmotionType.values()) {
            double weight = bps.getOrDefault(type, 0) / 10000.0;
            double[] anchor = VA_ANCHOR.get(type);
            v += weight * anchor[0];
            a += weight * anchor[1];
        }
        double intensity = Math.sqrt(v * v + a * a);
        return new int[]{
                (int) Math.round(v * 1000),
                (int) Math.round(a * 1000),
                (int) Math.round(intensity * 1000)
        };
    }
}
