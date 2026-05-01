package com.caring.infra.ai.gemini.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GeminiSegment(
        String timestamp,
        String text,
        List<GeminiEmotionScore> emotions,
        @JsonProperty("prosody_notes") String prosodyNotes
) {}
