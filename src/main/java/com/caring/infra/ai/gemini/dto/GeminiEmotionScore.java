package com.caring.infra.ai.gemini.dto;

public record GeminiEmotionScore(
        String label,
        String category,
        double intensity
) {}
