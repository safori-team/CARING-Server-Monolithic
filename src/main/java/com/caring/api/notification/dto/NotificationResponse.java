package com.caring.api.notification.dto;

import com.caring.domain.emotion.entity.EmotionType;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@Builder
@Getter
@RequiredArgsConstructor
public class NotificationResponse {
    private final Long id;
    private final Long voiceId;
    private final String name;
    private final EmotionType topEmotion;
    private final LocalDateTime createdAt;
}
