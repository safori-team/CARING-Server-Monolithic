package com.caring.infra.openai;

import com.caring.api.emotion.dto.WeekDayEmotion;
import com.caring.domain.emotion.entity.EmotionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

//TODO refactoring structure
@Component
@RequiredArgsConstructor
public class OpenAiWeeklyReportClient {

    private final ObjectMapper objectMapper;

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    public String generateWeeklyReport(String userName, List<WeekDayEmotion> weeklyEmotions) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured");
        }

        String userPrompt = buildUserPrompt(userName, weeklyEmotions);
        JsonNode response = WebClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri("/chat/completions")
                .bodyValue(Map.of(
                        "model", model,
                        "messages", List.of(
                                Map.of("role", "system", "content", weeklySystemPrompt()),
                                Map.of("role", "user", "content", userPrompt)
                        ),
                        "temperature", 0.7,
                        "max_completion_tokens", 400
                ))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null) {
            throw new IllegalStateException("OpenAI response is empty");
        }

        JsonNode contentNode = response.path("choices").path(0).path("message").path("content");
        String content = contentNode.isMissingNode() ? "" : contentNode.asText("").trim();
        if (content.isBlank()) {
            throw new IllegalStateException("OpenAI weekly report content is empty");
        }
        return content;
    }

    private String weeklySystemPrompt() {
        return "너는 노년층 혹은 장애인 케어 서비스의 감정 코치다. 한국어로 공감적이고 자연스럽게, 1~3문장으로 " +
                "주간 감정 추세를 반드시 요약해라. 데이터가 적어도 관찰 가능한 내용만 기반으로 요약하고, 추측하지 말고 " +
                "사실 중심으로 서술해라. 조언은 최소화하고 관찰 결과에 집중해라. 또한 초반/중반/후반 흐름을 구분하고, " +
                "감정 라벨은 {happy, sad, neutral, angry, anxiety, surprise} 집합만 사용한다. fear는 anxiety로 매핑한다.";
    }

    private String buildUserPrompt(String userName, List<WeekDayEmotion> weeklyEmotions) {
        String dailyLines = weeklyEmotions.isEmpty()
                ? "최근 7일 동안 감정 분석 데이터가 없습니다."
                : weeklyEmotions.stream()
                .map(emotion -> "- " + emotion.getDate() + ": " + toPromptEmotion(emotion.getEmotionType()))
                .collect(Collectors.joining("\n"));

        return "대상 사용자: " + userName + "\n" +
                "최근 7일 날짜별 대표 감정 목록:\n" +
                dailyLines + "\n\n" +
                "위 날짜별 감정 목록을 바탕으로 주간 감정 추세를 한 문단(1~3문장)으로 요약해줘. " +
                "초반/중반/후반 흐름을 구분하고, 불안은 anxiety로 표기해. 데이터가 적어도 관찰 가능한 내용만 바탕으로 요약해줘.";
    }

    private String toPromptEmotion(EmotionType emotionType) {
        if (emotionType == null) {
            return "unknown";
        }
        return emotionType == EmotionType.FEAR ? "anxiety" : emotionType.name().toLowerCase();
    }
}
