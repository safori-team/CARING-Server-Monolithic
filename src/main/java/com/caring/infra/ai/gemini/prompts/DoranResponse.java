package com.caring.infra.ai.gemini.prompts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DoranResponse(
        String empathy,
        @JsonProperty("detected_distortion") String detectedDistortion,
        String analysis,
        @JsonProperty("socratic_question") String socraticQuestion,
        @JsonProperty("alternative_thought") String alternativeThought,
        @JsonProperty("top_emotion") String topEmotion
) {
    public static DoranResponse fallback() {
        return new DoranResponse(
                "죄송해요, 잠시 생각이 꼬였나 봐요.",
                "없음",
                "내용을 불러오지 못했습니다.",
                "오늘 하루는 어떠셨나요?",
                "항상 응원합니다.",
                "neutral"
        );
    }
}
