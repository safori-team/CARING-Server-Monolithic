package com.caring.api.emotion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 특정 월의 세부 감정 버블차트 응답.
 * yearMonth: "2024-01" 형식
 */
@Getter
@Builder
@AllArgsConstructor
public class MonthlyEmotionBubbleResponse {
    private final String yearMonth;
    private final List<EmotionLabelItem> labels;
}
