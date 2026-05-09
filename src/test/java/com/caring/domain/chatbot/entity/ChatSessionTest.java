package com.caring.domain.chatbot.entity;

import com.caring.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class ChatSessionTest {

    @Test
    void create_assignsRandomUuid() {
        User user = User.builder().build();
        ChatSession s = ChatSession.create(user);
        assertThat(s.getId()).isNotBlank();
        assertThat(UUID.fromString(s.getId())).isNotNull();
    }

    @Test
    void create_uniqueIdsPerCall() {
        User user = User.builder().build();
        ChatSession s1 = ChatSession.create(user);
        ChatSession s2 = ChatSession.create(user);
        assertThat(s1.getId()).isNotEqualTo(s2.getId());
    }

    @Test
    void create_assignsUser() {
        User user = User.builder().build();
        ChatSession s = ChatSession.create(user);
        assertThat(s.getUser()).isSameAs(user);
    }
}
