package com.caring.api.chatbot.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatSessionItemResponse(
        String sessionId,
        String lastMessage,
        LocalDateTime lastUpdated,
        List<String> distortionTags,
        String emotion
) {}
