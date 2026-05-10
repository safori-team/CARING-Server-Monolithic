package com.caring.infra.ai.gemini.dto;

// emotions 배열의 각 원소: { "name": "joy", "intensity": 0.8 }
public record GeminiEmotionScore(
        String name,
        double intensity
) {}
