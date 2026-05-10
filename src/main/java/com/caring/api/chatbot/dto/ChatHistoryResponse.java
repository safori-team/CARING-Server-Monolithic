package com.caring.api.chatbot.dto;

import java.util.List;

/**
 * 채팅 상세 응답.
 * sessionId 메타 + 표준 페이징 형상(items, page, size, totalElements, totalPages, hasNext)을 합친 평탄 구조.
 */
public record ChatHistoryResponse(
        String sessionId,
        List<ChatMessageItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {}
