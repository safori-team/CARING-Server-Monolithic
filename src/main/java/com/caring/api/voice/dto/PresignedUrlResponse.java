package com.caring.api.voice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class PresignedUrlResponse {
    private final String presignedUrl;
    private final String voiceKey;
}
