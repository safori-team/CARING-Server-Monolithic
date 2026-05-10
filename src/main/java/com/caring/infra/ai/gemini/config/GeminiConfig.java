package com.caring.infra.ai.gemini.config;

import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Bean
    @ConditionalOnExpression("!'${gemini.api-key:}'.isEmpty()")
    public Client geminiClient() {
        return new Client.Builder()
                .apiKey(apiKey)
                .build();
    }
}
