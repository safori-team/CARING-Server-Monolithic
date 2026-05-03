package com.caring.api.emotion.service;

import com.caring.api.emotion.dto.EmotionLabelItem;
import com.caring.api.emotion.dto.MonthlyEmotionBubbleResponse;
import com.caring.common.annotation.UseCase;
import com.caring.domain.voice.adaptor.VoiceEmotionLabelAdaptor;

import java.time.YearMonth;
import java.util.List;

/**
 * 특정 월의 세부 감정 레이블 집계 조회 (버블차트용).
 * GET /v1/api/users/voices/analyzing/bubble?yearMonth=2024-01
 */
@UseCase
public class GetMonthlyEmotionBubbleUseCase {

    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;

    public GetMonthlyEmotionBubbleUseCase(VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor) {
        this.voiceEmotionLabelAdaptor = voiceEmotionLabelAdaptor;
    }

    public MonthlyEmotionBubbleResponse execute(String username, String yearMonth) {
        YearMonth ym = YearMonth.parse(yearMonth);   // "2024-01"
        List<Object[]> rows = voiceEmotionLabelAdaptor.findMonthlyLabelStats(
                username, ym.getYear(), ym.getMonthValue());

        List<EmotionLabelItem> labels = rows.stream()
                .map(row -> EmotionLabelItem.builder()
                        .label((String) row[0])
                        .category((String) row[1])
                        .diaryCount(((Number) row[2]).longValue())
                        .avgIntensityX1000(((Number) row[3]).intValue())
                        .build())
                .toList();

        return MonthlyEmotionBubbleResponse.builder()
                .yearMonth(yearMonth)
                .labels(labels)
                .build();
    }
}
