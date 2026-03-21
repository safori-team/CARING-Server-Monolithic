package com.caring.api.voice.dto;

import com.caring.domain.emotion.entity.EmotionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@Getter
@AllArgsConstructor
@Builder
@JsonInclude(NON_NULL)
public class VoiceListItem {
    private final Long voiceId;
    private final LocalDate createdAt;
    private final EmotionType emotion;
    private final String questionTitle;
    private final String content;
    private final String s3Url;
}
