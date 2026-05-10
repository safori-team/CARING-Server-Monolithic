package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ChatHistoryResponse;
import com.caring.api.chatbot.dto.ChatMessageItemResponse;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.MessageOrigin;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@UseCase
@RequiredArgsConstructor
public class GetChatHistoryUseCase {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;
    private final ChatbotDomainService chatbotDomainService;

    public ChatHistoryResponse execute(String username, String sessionId, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = DEFAULT_SIZE;
        if (size > MAX_SIZE) size = MAX_SIZE;

        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = chatSessionAdaptor.queryById(sessionId);
        chatbotDomainService.verifyOwnership(session, user);

        Page<ChatMessage> p = chatMessageAdaptor.queryBySessionId(
                sessionId, PageRequest.of(page - 1, size));
        List<ChatMessage> ordered = new ArrayList<>(p.getContent());
        Collections.reverse(ordered);

        List<ChatMessageItemResponse> messages = new ArrayList<>(ordered.size() * 2);
        for (ChatMessage m : ordered) {
            if (m.getOrigin() != MessageOrigin.MIND_DIARY) {
                messages.add(new ChatMessageItemResponse(
                        m.getId(),
                        "user",
                        m.getUserInput(),
                        m.getVoice() == null ? null : m.getVoice().getId(),
                        null, null, null, null, null, null, null, null, null,
                        m.getCreatedDate()
                ));
            }
            JsonNode bot = m.getBotResponse();
            String emotion = text(bot, "emotion");
            if (emotion == null) emotion = text(bot, "top_emotion");
            messages.add(new ChatMessageItemResponse(
                    m.getId(),
                    "assistant",
                    null, null,
                    text(bot, "empathy"),
                    text(bot, "detected_distortion"),
                    text(bot, "analysis"),
                    text(bot, "socratic_question"),
                    text(bot, "alternative_thought"),
                    emotion,
                    m.getFeedbackEmotion() == null ? null : m.getFeedbackEmotion().getCode(),
                    m.getFeedbackDetail(),
                    m.getFeedbackAt(),
                    m.getCreatedDate()
            ));
        }

        return new ChatHistoryResponse(
                sessionId,
                messages,
                page,
                size,
                p.getTotalElements(),
                p.getTotalPages() == 0 ? 1 : p.getTotalPages(),
                p.hasNext()
        );
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
