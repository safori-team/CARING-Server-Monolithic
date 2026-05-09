package com.caring.domain.chatbot.adaptor;

import com.caring.domain.chatbot.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface ChatMessageAdaptor {
    ChatMessage queryById(Long messageId);
    Page<ChatMessage> queryBySessionId(String sessionId, Pageable pageable);
    List<ChatMessage> queryRecentBySessionId(String sessionId, int limit);
    long countBySessionId(String sessionId);
    Optional<ChatMessage> queryLatestBySessionId(String sessionId);
    ChatMessage save(ChatMessage message);
}
