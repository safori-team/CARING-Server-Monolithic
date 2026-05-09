package com.caring.api.chatbot.dto;

import java.time.LocalDateTime;

public record ChatMessageItemResponse(
        Long messageId,
        String role,
        String content,
        Long voiceId,
        String empathy,
        String detectedDistortion,
        String analysis,
        String socraticQuestion,
        String alternativeThought,
        String emotion,
        String feedbackEmotion,
        String feedbackDetail,
        LocalDateTime feedbackAt,
        LocalDateTime timestamp
) {}
