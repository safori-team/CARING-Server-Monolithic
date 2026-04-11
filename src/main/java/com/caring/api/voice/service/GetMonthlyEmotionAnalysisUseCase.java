package com.caring.api.voice.service;

import com.caring.api.emotion.dto.MonthlyAnalysisCombinedResponse;
import com.caring.api.emotion.service.GetMonthlyEmotionReportUseCase;
import com.caring.common.annotation.UseCase;
import com.caring.common.util.DateRangeUtil;
import com.caring.domain.emotion.entity.EmotionType;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.entity.VoiceComposite;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UseCase
@RequiredArgsConstructor
public class GetMonthlyEmotionAnalysisUseCase {

    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final GetMonthlyEmotionReportUseCase getMonthlyEmotionReportUseCase;

    public MonthlyAnalysisCombinedResponse execute(String username, String month) {
        DateRangeUtil.DateRange range = DateRangeUtil.monthRange(month);

        List<VoiceComposite> composites = voiceCompositeAdaptor.queryByUsernameAndDateRange(
                username,
                range.getStart(),
                range.getEnd()
        );

        Map<EmotionType, Long> monthlyEmotionCounts = Arrays.stream(EmotionType.values())
                .collect(Collectors.toMap(
                        e -> e,
                        e -> composites.stream()
                                .filter(vc -> e.equals(vc.getTopEmotion()))
                                .count()
                ));

        EmotionType topEmotion = monthlyEmotionCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        String reportMessage = getMonthlyEmotionReportUseCase.execute(
                username,
                month,
                monthlyEmotionCounts,
                composites
        );

        return MonthlyAnalysisCombinedResponse.builder()
                .monthlyEmotionCounts(monthlyEmotionCounts)
                .topEmotion(topEmotion)
                .totalCount(composites.size())
                .reportMessage(reportMessage)
                .build();
    }
}
