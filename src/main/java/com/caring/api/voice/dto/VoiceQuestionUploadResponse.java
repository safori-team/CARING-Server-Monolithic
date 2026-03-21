package com.caring.api.voice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Getter
@AllArgsConstructor
@Builder
@JsonInclude(NON_NULL)
public class VoiceQuestionUploadResponse {
    private final boolean success;
    private final String message;
    private final Long voiceId;
    private final Long questionId;
}
