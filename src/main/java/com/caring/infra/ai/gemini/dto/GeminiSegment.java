package com.caring.infra.ai.gemini.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// Gemini responseSchema로 구조 강제:
// category → segment 단위 대분류
// emotions → [{ name, intensity }] 배열
public record GeminiSegment(
        String timestamp,
        String text,
        String category,
        List<GeminiEmotionScore> emotions,
        @JsonProperty("prosody_notes") String prosodyNotes
) {}
