package com.caring.api.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

public record VoiceReframingRequest(
        @NotBlank String sessionId,
        @NotBlank String userInput,
        Long voiceId
) {}
