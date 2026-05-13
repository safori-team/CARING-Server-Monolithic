package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ChatSessionItemResponse;
import com.caring.api.common.dto.PagedResponse;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.service.ChatbotMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;

@UseCase
@RequiredArgsConstructor
public class GetChatSessionsUseCase {

    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;
    private final ChatbotMessageMapper mapper;

    public PagedResponse<ChatSessionItemResponse> execute(String username, int page, int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;

        Page<ChatSession> sessionPage = chatSessionAdaptor.queryByUsername(
                username, PageRequest.of(page - 1, size));

        // N+1 제거: 페이지 내 모든 세션의 최신 메시지를 한 번에 조회
        List<String> sessionIds = sessionPage.getContent().stream().map(ChatSession::getId).toList();
        Map<String, ChatMessage> latestBySession = chatMessageAdaptor.queryLatestBySessionIds(sessionIds);

        Page<ChatSessionItemResponse> mapped = sessionPage.map(s -> toItem(s, latestBySession.get(s.getId())));
        return PagedResponse.from(mapped);
    }

    private ChatSessionItemResponse toItem(ChatSession s, ChatMessage latest) {
        String lastMessage = latest == null ? null : latest.getUserInput();
        // 마음일기 트리거(USER 발화 없음) → 봇 empathy 첫 줄을 미리보기로 fallback
        if (lastMessage == null && latest != null) {
            lastMessage = mapper.botMessagePreview(latest.getBotResponse());
        }

        String emotion = latest == null ? null : mapper.emotion(latest.getBotResponse());
        if (latest != null && latest.getFeedbackEmotion() != null) {
            emotion = latest.getFeedbackEmotion().getCode();
        }

        return new ChatSessionItemResponse(
                s.getId(),
                lastMessage,
                s.getLastMessageAt(),
                latest == null ? List.of() : mapper.distortionTags(latest.getBotResponse()),
                emotion
        );
    }
}
