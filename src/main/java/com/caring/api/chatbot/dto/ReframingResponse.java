package com.caring.api.chatbot.dto;

public record ReframingResponse(
        Long messageId,
        String empathy,
        String detectedDistortion,
        String analysis,
        String socraticQuestion,
        String alternativeThought,
        String emotion
) {}
