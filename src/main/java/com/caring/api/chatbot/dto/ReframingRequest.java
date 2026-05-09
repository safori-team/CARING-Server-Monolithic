package com.caring.api.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

public record ReframingRequest(
        @NotBlank String sessionId,
        @NotBlank String userInput,
        String emotion
) {}
