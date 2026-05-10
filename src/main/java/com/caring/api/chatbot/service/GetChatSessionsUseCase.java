package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ChatSessionItemResponse;
import com.caring.api.common.dto.PagedResponse;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

@UseCase
@RequiredArgsConstructor
public class GetChatSessionsUseCase {

    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;

    public PagedResponse<ChatSessionItemResponse> execute(String username, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;

        Page<ChatSession> sessionPage = chatSessionAdaptor.queryByUsername(
                username, PageRequest.of(page - 1, size));

        Page<ChatSessionItemResponse> mapped = sessionPage.map(this::toItem);
        return PagedResponse.from(mapped);
    }

    private ChatSessionItemResponse toItem(ChatSession s) {
        Optional<ChatMessage> latest = chatMessageAdaptor.queryLatestBySessionId(s.getId());
        String lastMessage = latest.map(ChatMessage::getUserInput).orElse(null);
        String emotion = latest.map(m -> readEmotion(m.getBotResponse())).orElse(null);
        List<String> tags = latest.map(m -> readDistortionTags(m.getBotResponse())).orElse(List.of());

        if (latest.isPresent() && latest.get().getFeedbackEmotion() != null) {
            emotion = latest.get().getFeedbackEmotion().getCode();
        }

        return new ChatSessionItemResponse(
                s.getId(),
                lastMessage,
                s.getLastModifiedDate(),
                tags,
                emotion
        );
    }

    private String readEmotion(JsonNode botResponse) {
        if (botResponse == null) return null;
        JsonNode n = botResponse.get("emotion");
        if (n == null) n = botResponse.get("top_emotion");
        return n == null || n.isNull() ? null : n.asText();
    }

    private List<String> readDistortionTags(JsonNode botResponse) {
        if (botResponse == null) return List.of();
        JsonNode n = botResponse.get("detected_distortion");
        if (n == null || n.isNull()) return List.of();
        String value = n.asText();
        if (value.isBlank() || "없음".equals(value)) return List.of();
        return List.of(value);
    }
}
