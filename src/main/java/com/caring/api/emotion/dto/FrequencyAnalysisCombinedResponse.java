package com.caring.api.emotion.dto;

import com.caring.domain.emotion.entity.EmotionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Getter
@Builder
@AllArgsConstructor
@JsonInclude(NON_NULL)
public class FrequencyAnalysisCombinedResponse {

    private final Map<EmotionType, Long> emotionFrequency;
    private final EmotionType topEmotion;
    private final long totalCount;
}
