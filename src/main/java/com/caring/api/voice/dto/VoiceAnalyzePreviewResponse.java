package com.caring.api.voice.dto;

import com.caring.domain.emotion.entity.EmotionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Getter
@AllArgsConstructor
@Builder
@JsonInclude(NON_NULL)
public class VoiceAnalyzePreviewResponse {
    private Long voiceId;

    private int happyBps;
    private int sadBps;
    private int neutralBps;
    private int angryBps;

    @Builder.Default
    private Integer anxietyBps = 0;

    private int surpriseBps;

    private EmotionType topEmotion;
    private Integer topConfidenceBps;
    private String modelVersion;
}
