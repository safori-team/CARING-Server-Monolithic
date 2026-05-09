package com.caring.infra.ai.gemini.prompts;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReframingPromptTest {

    @Test
    void build_includesUserInputAndTurnCount() {
        String prompt = ReframingPrompt.build(
                "오늘 너무 힘들어요",
                List.of(),
                3,
                null
        );
        assertThat(prompt).contains("오늘 너무 힘들어요");
        assertThat(prompt).contains("3번째 대화");
    }

    @Test
    void build_includesDistortionGuide() {
        String prompt = ReframingPrompt.build("test", List.of(), 1, null);
        assertThat(prompt).contains("흑백사고");
        assertThat(prompt).contains("긍정 정서 강화");
    }

    @Test
    void build_includesEmotionStrategy_whenEmotionProvided() {
        String prompt = ReframingPrompt.build("test", List.of(), 1, "sad");
        assertThat(prompt).contains("슬픔을 충분히 인정");
    }

    @Test
    void build_emptyHistory_includesNoneMarker() {
        String prompt = ReframingPrompt.build("test", List.of(), 1, null);
        assertThat(prompt).contains("(없음. 대화 시작)");
    }

    @Test
    void build_withHistory_formatsTurns() {
        List<ReframingPrompt.HistoryTurn> history = List.of(
                new ReframingPrompt.HistoryTurn("어제 우울했어", "오늘은 어떠세요?")
        );
        String prompt = ReframingPrompt.build("좀 나아졌어요", history, 2, null);
        assertThat(prompt).contains("Turn 1:");
        assertThat(prompt).contains("어제 우울했어");
        assertThat(prompt).contains("오늘은 어떠세요?");
    }
}
