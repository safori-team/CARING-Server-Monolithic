package com.caring.infra.ai.gemini;

import com.caring.infra.ai.gemini.prompts.DoranResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiChatbotClientTest {

    @Test
    void generate_clientNotConfigured_returnsFallback() {
        GeminiChatbotClient client = new GeminiChatbotClient(
                Optional.empty(), "gemini-2.5-pro", new ObjectMapper());
        DoranResponse r = client.generate("any prompt");
        assertThat(r.empathy()).contains("죄송");
        assertThat(r.topEmotion()).isEqualTo("neutral");
    }
}
