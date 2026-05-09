package com.caring.domain.chatbot.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoranEmotionTest {

    @Test
    void fromCode_validCodes_returnsEnum() {
        assertThat(DoranEmotion.fromCode("happy")).isEqualTo(DoranEmotion.HAPPY);
        assertThat(DoranEmotion.fromCode("sad")).isEqualTo(DoranEmotion.SAD);
        assertThat(DoranEmotion.fromCode("neutral")).isEqualTo(DoranEmotion.NEUTRAL);
        assertThat(DoranEmotion.fromCode("angry")).isEqualTo(DoranEmotion.ANGRY);
        assertThat(DoranEmotion.fromCode("anxiety")).isEqualTo(DoranEmotion.ANXIETY);
        assertThat(DoranEmotion.fromCode("surprise")).isEqualTo(DoranEmotion.SURPRISE);
    }

    @Test
    void fromCode_caseInsensitive() {
        assertThat(DoranEmotion.fromCode("HAPPY")).isEqualTo(DoranEmotion.HAPPY);
        assertThat(DoranEmotion.fromCode("Sad")).isEqualTo(DoranEmotion.SAD);
    }

    @Test
    void fromCode_invalid_throws() {
        assertThatThrownBy(() -> DoranEmotion.fromCode("joy"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getCode_returnsLowercaseName() {
        assertThat(DoranEmotion.HAPPY.getCode()).isEqualTo("happy");
        assertThat(DoranEmotion.ANXIETY.getCode()).isEqualTo("anxiety");
    }
}
