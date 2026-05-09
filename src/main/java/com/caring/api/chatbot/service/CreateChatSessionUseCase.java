package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.CreateSessionResponse;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class CreateChatSessionUseCase {

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;

    public CreateSessionResponse execute(String username) {
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = ChatSession.create(user);
        ChatSession saved = chatSessionAdaptor.save(session);
        return new CreateSessionResponse(saved.getId());
    }
}
