package com.caring.api.voice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Getter
@AllArgsConstructor
@Builder
@JsonInclude(NON_NULL)
public class VoiceDetailResponse {
    private final Long voiceId;
    private final String title;
    private final String topEmotion;
    private final LocalDateTime createdAt;
    private final String voiceContent;
    private final String s3Url;
}
