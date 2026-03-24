package com.caring.infra.ai.hume.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class HumeConfig {

    @Value("${hume.api-key}")
    private String apiKey;

    @Value("${hume.base-url}")
    private String baseUrl;

    @Value("${hume.callback-url}")
    private String callbackUrl;

    @Bean
    public WebClient humeWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Hume-Api-Key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Bean
    public String humeCallbackUrl() {
        return callbackUrl;
    }
}
