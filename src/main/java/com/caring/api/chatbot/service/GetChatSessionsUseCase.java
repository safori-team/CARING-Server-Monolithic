package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ChatSessionItemResponse;
import com.caring.api.chatbot.dto.SessionListResponse;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@UseCase
@RequiredArgsConstructor
public class GetChatSessionsUseCase {

    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;

    public SessionListResponse execute(String username) {
        List<ChatSession> sessions = chatSessionAdaptor.queryByUsername(username);
        List<ChatSessionItemResponse> items = new ArrayList<>(sessions.size());
        for (ChatSession s : sessions) {
            Optional<ChatMessage> latest = chatMessageAdaptor.queryLatestBySessionId(s.getId());
            String lastMessage = latest.map(ChatMessage::getUserInput).orElse(null);
            String emotion = latest.map(m -> readEmotion(m.getBotResponse())).orElse(null);
            List<String> tags = latest.map(m -> readDistortionTags(m.getBotResponse())).orElse(List.of());

            if (latest.isPresent() && latest.get().getFeedbackEmotion() != null) {
                emotion = latest.get().getFeedbackEmotion().getCode();
            }

            items.add(new ChatSessionItemResponse(
                    s.getId(),
                    lastMessage,
                    s.getLastModifiedDate(),
                    tags,
                    emotion
            ));
        }
        return new SessionListResponse(items);
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
