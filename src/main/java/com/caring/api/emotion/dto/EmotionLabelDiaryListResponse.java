package com.caring.api.emotion.dto;

import com.caring.api.voice.dto.VoiceListItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 특정 세부 감정을 느꼈던 일기 리스트 응답.
 */
@Getter
@Builder
@AllArgsConstructor
public class EmotionLabelDiaryListResponse {
    private final String yearMonth;
    private final String label;
    private final String category;
    private final List<VoiceListItem> diaries;
}
