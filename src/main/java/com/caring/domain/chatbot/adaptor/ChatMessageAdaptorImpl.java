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

import java.util.List;
import java.util.Optional;

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
    @Transactional
    public ChatMessage save(ChatMessage message) {
        return repository.save(message);
    }
}
