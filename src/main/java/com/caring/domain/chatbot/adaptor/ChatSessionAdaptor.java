package com.caring.domain.chatbot.adaptor;

import com.caring.domain.chatbot.entity.ChatSession;
import java.util.List;

public interface ChatSessionAdaptor {
    ChatSession queryById(String sessionId);
    List<ChatSession> queryByUsername(String username);
    ChatSession save(ChatSession session);
    void delete(ChatSession session);
}
