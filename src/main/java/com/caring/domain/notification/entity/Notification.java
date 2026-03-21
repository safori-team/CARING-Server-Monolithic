package com.caring.domain.notification.entity;

import com.caring.domain.common.entity.BaseTimeEntity;
import com.caring.domain.emotion.entity.EmotionType;
import com.caring.domain.voice.entity.Voice;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@SuperBuilder
@Table(name = "notification",
        indexes = {
                @Index(name = "idx_notification_voice", columnList = "voice_id"),
                @Index(name = "idx_notification_created", columnList = "created_at")
        }
)
public class Notification extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "voice_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_notification_voice")
    )
    private Voice voice;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "top_emotion", length = 16)
    private EmotionType topEmotion;


}
