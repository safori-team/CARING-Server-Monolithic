package com.caring.api.chatbot.dto;

import java.util.List;

public record ChatHistoryResponse(
        String sessionId,
        List<ChatMessageItemResponse> messages,
        int totalPage,
        int currentPage
) {}
