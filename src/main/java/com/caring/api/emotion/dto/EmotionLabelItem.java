package com.caring.api.emotion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 버블차트 단일 감정 레이블 항목.
 * diaryCount: 해당 월에 이 label이 등장한 일기 수 (버블 크기 기준)
 * avgIntensityX1000: 평균 intensity × 1000 (버블 가중치 참고용)
 */
@Getter
@Builder
@AllArgsConstructor
public class EmotionLabelItem {
    private final String label;          // joy, anxiety, frustration …
    private final String category;       // happy / sad / neutral / angry / fear / surprise
    private final long diaryCount;       // 해당 월에 등장한 일기 수
    private final int avgIntensityX1000; // 평균 intensity × 1000
}
