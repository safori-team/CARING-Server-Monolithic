package com.caring.infra.ai.hume.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
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
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(16 * 1024 * 1024)) // 16MB
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Hume-Api-Key", apiKey)
                .defaultHeader("Content-Type", "application/json")
                .exchangeStrategies(strategies)
                .build();
    }

    @Bean
    public String humeCallbackUrl() {
        return callbackUrl;
    }
}
