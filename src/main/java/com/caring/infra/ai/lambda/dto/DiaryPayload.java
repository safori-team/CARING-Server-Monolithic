package com.caring.infra.ai.lambda.dto;

import com.caring.infra.ai.hume.dto.processed.EmotionAnalysis;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record DiaryPayload(
        String source,
        @JsonProperty("user_id") String userId,
        @JsonProperty("user_name") String userName,
        String question,
        String content,
        @JsonProperty("s3_url") String s3Url,
        @JsonProperty("recorded_at") String recordedAt,
        @JsonProperty("emotion_analysis") EmotionAnalysis emotionAnalysis
) {
    public static DiaryPayload ofMindDiary(
            String userId,
            String userName,
            String question,
            String content,
            String s3Url,
            String recordedAt,
            EmotionAnalysis emotionAnalysis
    ) {
        return DiaryPayload.builder()
                .source("mind-diary")
                .userId(userId)
                .userName(userName)
                .question(question)
                .content(content)
                .s3Url(s3Url)
                .recordedAt(recordedAt)
                .emotionAnalysis(emotionAnalysis)
                .build();
    }
}
