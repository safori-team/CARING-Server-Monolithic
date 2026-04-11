package com.caring.api.emotion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(NON_NULL)
public class WeeklyAnalysisCombinedResponse {

    private final List<WeekDayEmotion> weeklyEmotions;
    private final String reportMessage;
}
