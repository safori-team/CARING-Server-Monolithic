package com.caring.infra.ai.gemini;

import com.caring.infra.ai.gemini.prompts.DoranResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class GeminiChatbotClient {

    private static final List<String> DISTORTION_ENUM = List.of(
            "흑백사고", "선택적 추상", "자의적 추론", "과잉일반화", "확대/축소",
            "개인화", "정서적 추론", "긍정 격하", "파국화", "잘못된 별칭 붙이기",
            "긍정 정서 강화", "위기 상황", "없음"
    );

    private static final List<String> EMOTION_ENUM = List.of(
            "happy", "sad", "neutral", "angry", "anxiety", "surprise"
    );

    private final Optional<Client> geminiClient;
    private final String modelName;
    private final ObjectMapper objectMapper;

    public GeminiChatbotClient(
            Optional<Client> geminiClient,
            @Value("${gemini.chatbot-model:gemini-2.5-pro}") String modelName,
            ObjectMapper objectMapper
    ) {
        this.geminiClient = geminiClient;
        this.modelName = modelName;
        this.objectMapper = objectMapper;
    }

    public DoranResponse generate(String prompt) {
        if (geminiClient.isEmpty()) {
            log.warn("GeminiChatbotClient: no Gemini client configured, returning fallback");
            return DoranResponse.fallback();
        }
        try {
            GenerateContentResponse response = geminiClient.get().models.generateContent(
                    modelName,
                    Content.builder()
                            .role("user")
                            .parts(List.of(Part.builder().text(prompt).build()))
                            .build(),
                    GenerateContentConfig.builder()
                            .temperature(0.7f)
                            .maxOutputTokens(2048)
                            .responseMimeType("application/json")
                            .responseSchema(buildResponseSchema())
                            .build()
            );

            String text = response.text();
            if (text == null || text.isBlank()) {
                log.warn("GeminiChatbotClient: empty response, returning fallback");
                return DoranResponse.fallback();
            }
            return objectMapper.readValue(text, DoranResponse.class);
        } catch (Exception e) {
            log.error("GeminiChatbotClient.generate failed", e);
            return DoranResponse.fallback();
        }
    }

    private Schema buildResponseSchema() {
        Map<String, Schema> props = new HashMap<>();
        props.put("empathy", Schema.builder().type(Type.Known.STRING).build());
        props.put("detected_distortion", Schema.builder()
                .type(Type.Known.STRING)
                .enum_(DISTORTION_ENUM)
                .build());
        props.put("analysis", Schema.builder().type(Type.Known.STRING).build());
        props.put("socratic_question", Schema.builder().type(Type.Known.STRING).build());
        props.put("alternative_thought", Schema.builder().type(Type.Known.STRING).build());
        props.put("top_emotion", Schema.builder()
                .type(Type.Known.STRING)
                .enum_(EMOTION_ENUM)
                .build());

        return Schema.builder()
                .type(Type.Known.OBJECT)
                .properties(props)
                .required(List.of("empathy", "detected_distortion", "analysis",
                        "socratic_question", "alternative_thought", "top_emotion"))
                .build();
    }
}
