package com.caring.domain.chatbot.entity;

import com.caring.domain.common.entity.BaseTimeEntity;
import com.caring.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "chat_session", indexes = {
        @Index(name = "idx_chat_session_user_modified",
               columnList = "user_id, lastModifiedDate DESC")
})
public class ChatSession extends BaseTimeEntity {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    public static ChatSession create(User user) {
        return ChatSession.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .build();
    }
}
