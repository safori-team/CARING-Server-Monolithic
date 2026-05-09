package com.caring.domain.chatbot.entity;

import com.caring.domain.common.entity.BaseTimeEntity;
import com.caring.domain.voice.entity.Voice;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "chat_message", indexes = {
        @Index(name = "idx_chat_message_session_created",
               columnList = "session_id, createdDate DESC"),
        @Index(name = "idx_chat_message_voice", columnList = "voice_id")
})
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_message_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, columnDefinition = "CHAR(36)")
    private ChatSession session;

    @Column(columnDefinition = "TEXT")
    private String userInput;

    @Convert(converter = JsonNodeConverter.class)
    @Column(name = "bot_response", nullable = false, columnDefinition = "JSON")
    private JsonNode botResponse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voice_id")
    private Voice voice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private DoranEmotion feedbackEmotion;

    @Column(columnDefinition = "TEXT")
    private String feedbackDetail;

    @Column
    private LocalDateTime feedbackAt;

    public void applyFeedback(DoranEmotion emotion, String detail) {
        this.feedbackEmotion = emotion;
        this.feedbackDetail = detail;
        this.feedbackAt = LocalDateTime.now();
    }
}
