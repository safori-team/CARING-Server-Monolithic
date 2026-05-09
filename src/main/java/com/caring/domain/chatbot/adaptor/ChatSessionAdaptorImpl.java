package com.caring.domain.chatbot.adaptor;

import com.caring.common.annotation.Adaptor;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.exception.ChatbotHandler;
import com.caring.domain.chatbot.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Adaptor
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ChatSessionAdaptorImpl implements ChatSessionAdaptor {

    private final ChatSessionRepository repository;

    @Override
    public ChatSession queryById(String sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> ChatbotHandler.SESSION_NOT_FOUND);
    }

    @Override
    public List<ChatSession> queryByUsername(String username) {
        return repository.findByUser_UsernameOrderByLastModifiedDateDesc(username);
    }

    @Override
    @Transactional
    public ChatSession save(ChatSession session) {
        return repository.save(session);
    }

    @Override
    @Transactional
    public void delete(ChatSession session) {
        repository.delete(session);
    }
}
