package com.caring.domain.chatbot.adaptor;

import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.exception.ChatbotHandler;
import com.caring.domain.chatbot.repository.ChatSessionRepository;
import com.caring.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChatSessionAdaptorImplTest {

    @Mock ChatSessionRepository repository;
    @InjectMocks ChatSessionAdaptorImpl adaptor;

    @Test
    void queryById_found_returnsSession() {
        ChatSession s = ChatSession.create(User.builder().build());
        given(repository.findById(s.getId())).willReturn(Optional.of(s));
        assertThat(adaptor.queryById(s.getId())).isSameAs(s);
    }

    @Test
    void queryById_notFound_throws() {
        given(repository.findById("missing")).willReturn(Optional.empty());
        assertThatThrownBy(() -> adaptor.queryById("missing"))
                .isSameAs(ChatbotHandler.SESSION_NOT_FOUND);
    }
}
