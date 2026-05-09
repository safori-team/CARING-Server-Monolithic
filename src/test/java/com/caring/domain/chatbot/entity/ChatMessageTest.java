package com.caring.domain.chatbot.entity;

import com.caring.domain.user.entity.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Test
    void applyFeedback_setsFeedbackFields() throws Exception {
        ChatSession session = ChatSession.create(User.builder().build());
        JsonNode botResponse = OM.readTree("{\"empathy\":\"hi\",\"emotion\":\"sad\"}");
        ChatMessage msg = ChatMessage.builder()
                .session(session)
                .userInput("hi")
                .botResponse(botResponse)
                .origin(MessageOrigin.USER_TEXT)
                .build();

        msg.applyFeedback(DoranEmotion.ANXIETY, "사실 불안이 더 컸어요");

        assertThat(msg.getFeedbackEmotion()).isEqualTo(DoranEmotion.ANXIETY);
        assertThat(msg.getFeedbackDetail()).isEqualTo("사실 불안이 더 컸어요");
        assertThat(msg.getFeedbackAt()).isNotNull();
    }

    @Test
    void applyFeedback_overwritesPrevious() throws Exception {
        ChatSession session = ChatSession.create(User.builder().build());
        JsonNode botResponse = OM.readTree("{}");
        ChatMessage msg = ChatMessage.builder()
                .session(session).botResponse(botResponse).origin(MessageOrigin.USER_TEXT).build();

        msg.applyFeedback(DoranEmotion.SAD, "first");
        msg.applyFeedback(DoranEmotion.HAPPY, "changed");

        assertThat(msg.getFeedbackEmotion()).isEqualTo(DoranEmotion.HAPPY);
        assertThat(msg.getFeedbackDetail()).isEqualTo("changed");
    }
}
