package com.caring.infra.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final WebClient aiWebClient;

    public void sendVoiceForAnalysis(String s3Url, Long voiceId) {
        // WebClient + subscribe()는 fire-and-forget 비동기 호출이다.
        aiWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/analyze/chat")
                        .queryParam("s3_url", s3Url)
                        .queryParam("voice_id", voiceId)
                        .build())
                .retrieve()
                .bodyToMono(Void.class)
                .subscribe(
                        null,
                        error -> log.error("[AiServerClient] AI 서버 호출 실패 - voiceId={}, error={}", voiceId, error.getMessage())
                );
    }
}
