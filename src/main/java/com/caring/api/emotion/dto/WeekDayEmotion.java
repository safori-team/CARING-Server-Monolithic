package com.caring.api.emotion.dto;

import com.caring.api.common.dto.WeekDay;
import com.caring.domain.emotion.entity.EmotionType;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Builder
@Getter
@RequiredArgsConstructor
public class WeekDayEmotion {
    private final LocalDate date;
    private final WeekDay weekDay;
    private final EmotionType emotionType;
}
