package com.caring.api.chatbot.dto;

import java.time.LocalDateTime;

public record FeedbackResponse(
        Long messageId,
        String feedbackEmotion,
        String feedbackDetail,
        LocalDateTime feedbackAt
) {}
