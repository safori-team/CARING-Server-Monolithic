package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.util.DateRangeUtil;
import com.caring.domain.emotion.entity.EmotionType;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.api.emotion.dto.FrequencyAnalysisCombinedResponse;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UseCase
@RequiredArgsConstructor
public class GetEmotionFrequencyUseCase {

    private final VoiceCompositeAdaptor voiceCompositeAdaptor;

    public FrequencyAnalysisCombinedResponse execute(String username, String month) {
        DateRangeUtil.DateRange range = DateRangeUtil.monthRange(month);

        List<VoiceComposite> composites = voiceCompositeAdaptor.queryByUsernameAndDateRange(
                username,
                range.getStart(),
                range.getEnd()
        );

        Map<EmotionType, Long> emotionFrequency = Arrays.stream(EmotionType.values())
                .collect(Collectors.toMap(
                        e -> e,
                        e -> composites.stream()
                                .filter(vc -> e.equals(vc.getTopEmotion()))
                                .count()
                ));

        EmotionType topEmotion = emotionFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        return FrequencyAnalysisCombinedResponse.builder()
                .emotionFrequency(emotionFrequency)
                .topEmotion(topEmotion)
                .totalCount(composites.size())
                .build();
    }
}
