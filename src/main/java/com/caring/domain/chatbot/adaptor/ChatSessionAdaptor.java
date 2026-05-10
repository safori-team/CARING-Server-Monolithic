package com.caring.domain.chatbot.adaptor;

import com.caring.domain.chatbot.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ChatSessionAdaptor {
    ChatSession queryById(String sessionId);
    Page<ChatSession> queryByUsername(String username, Pageable pageable);
    ChatSession save(ChatSession session);
    void delete(ChatSession session);
}

