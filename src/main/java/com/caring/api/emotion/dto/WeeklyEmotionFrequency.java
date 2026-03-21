package com.caring.api.emotion.dto;

import com.caring.domain.emotion.entity.EmotionType;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Builder
@Getter
@RequiredArgsConstructor
public class WeeklyEmotionFrequency {
    private final int weekIndex;
    private final int count;
    private final EmotionType emotionType;
}
