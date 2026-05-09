package com.caring.api.chatbot.dto;

import jakarta.validation.constraints.NotBlank;

public record FeedbackRequest(
        @NotBlank String emotion,
        String detail
) {}
