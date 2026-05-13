package com.caring.domain.chatbot.entity;

import com.caring.domain.common.entity.BaseTimeEntity;
import com.caring.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "chat_session", indexes = {
        @Index(name = "idx_chat_session_user_last_message",
               columnList = "user_id, lastMessageAt DESC")
})
public class ChatSession extends BaseTimeEntity {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 마지막으로 메시지가 추가된 시각. 세션 목록 정렬 기준.
     * BaseTimeEntity의 {@code lastModifiedDate}와 별개로 메시지 INSERT마다 명시적 갱신.
     */
    @Column
    private LocalDateTime lastMessageAt;

    public static ChatSession create(User user) {
        LocalDateTime now = LocalDateTime.now();
        return ChatSession.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .lastMessageAt(now)
                .build();
    }

    /** 메시지 추가 시 호출. 세션 정렬 기준을 갱신한다. */
    public void touch() {
        this.lastMessageAt = LocalDateTime.now();
    }
}
