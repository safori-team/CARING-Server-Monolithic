package com.caring.domain.chatbot.adaptor;

import com.caring.common.annotation.Adaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.exception.ChatbotHandler;
import com.caring.domain.chatbot.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Adaptor
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatMessageAdaptorImpl implements ChatMessageAdaptor {

    private final ChatMessageRepository repository;

    @Override
    public ChatMessage queryById(Long messageId) {
        return repository.findById(messageId)
                .orElseThrow(() -> ChatbotHandler.MESSAGE_NOT_FOUND);
    }

    @Override
    public ChatMessage queryByIdWithSessionAndUser(Long messageId) {
        return repository.findByIdWithSessionAndUser(messageId)
                .orElseThrow(() -> ChatbotHandler.MESSAGE_NOT_FOUND);
    }

    @Override
    public Page<ChatMessage> queryBySessionId(String sessionId, Pageable pageable) {
        return repository.findBySession_IdOrderByCreatedDateDesc(sessionId, pageable);
    }

    @Override
    public List<ChatMessage> queryRecentBySessionId(String sessionId, int limit) {
        return repository.findRecentBySessionId(sessionId, PageRequest.of(0, limit));
    }

    @Override
    public long countBySessionId(String sessionId) {
        return repository.countBySession_Id(sessionId);
    }

    @Override
    public Optional<ChatMessage> queryLatestBySessionId(String sessionId) {
        return repository.findTopBySession_IdOrderByCreatedDateDesc(sessionId);
    }

    @Override
    public Map<String, ChatMessage> queryLatestBySessionIds(List<String> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) return new HashMap<>();
        return repository.findLatestBySessionIds(sessionIds).stream()
                .collect(Collectors.toMap(
                        m -> m.getSession().getId(),
                        Function.identity(),
                        // 같은 세션에 동시각 메시지가 두 건 있으면 PK 큰(최신) 것을 채택
                        (a, b) -> a.getId() > b.getId() ? a : b
                ));
    }

    @Override
    @Transactional
    public ChatMessage save(ChatMessage message) {
        return repository.save(message);
    }
}
