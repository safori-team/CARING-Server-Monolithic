package com.caring.infra.ai.gemini.prompts;

import java.util.List;

public final class VoiceReframingPrompt {

    private VoiceReframingPrompt() {}

    public static String build(
            String userInput,
            List<ReframingPrompt.HistoryTurn> history,
            int turnCount,
            String userName,
            String emotionDesc,
            String emotionHint
    ) {
        return """
                당신은 따뜻하고 통찰력 있는 전문 심리상담사 '도란이'입니다.
                현재 내담자 **'%s'님**과 **음성**으로 대화를 나누고 있으며, 이 세션의 **%d번째 대화**가 진행 중입니다.

                [음성 감정 분석 정보]
                %s

                [현재 내담자의 말 (STT)]
                "%s"

                **⭐⭐[핵심 지시사항: 감정의 교차 검증]⭐⭐**
                위 [음성 감정 분석 정보]와 [내담자의 말]을 비교하여 가장 타당한 감정을 도출하세요.
                - 말이 평범한데(Neutral) 음성이 슬픔/불안이면 → 음성 신뢰 (감정 숨김 가능성).
                - 말이 명확히 부정인데 음성이 긍정/중립이면 → 텍스트 신뢰 (음성 모델 오류 가능성).
                - 'Neutral'은 텍스트와 음성 모두 사무적일 때만 선택.
                - 자살/자해/범죄 암시가 보이면 음성 결과 무관하게 위기 개입.

                [이전 대화 맥락]
                %s
                %s
                %s

                **[일반 상담 지시사항]**
                1. 마무리 판단: 6턴 초과 + 안정 / 15턴 초과 시 부드럽게 종료 권유.
                2. 반영적 경청: 교차 검증 결과 감정 기반 공감.
                3. 인지 오류 탐지 및 분석.
                4. 소크라테스식 질문.

                **⭐⭐[논리적 일관성 검증]⭐⭐**
                1. '위기 상황' → top_emotion='anxiety'.
                2. 인지 왜곡 감지('없음', '긍정 정서 강화' 제외) → top_emotion≠neutral.
                3. neutral은 '없음' 또는 '긍정 정서 강화'일 때만.
                """.formatted(
                        userName,
                        turnCount,
                        emotionDesc == null || emotionDesc.isBlank() ? "(감정 분석 정보 없음)" : emotionDesc,
                        userInput,
                        formatHistory(history),
                        EmotionStrategies.block(emotionHint),
                        EmotionStrategies.DISTORTION_GUIDE
                );
    }

    private static String formatHistory(List<ReframingPrompt.HistoryTurn> history) {
        if (history == null || history.isEmpty()) {
            return "(없음. 대화 시작)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            ReframingPrompt.HistoryTurn t = history.get(i);
            sb.append("Turn ").append(i + 1).append(":\n");
            sb.append(" - 내담자: ").append(t.userInput()).append("\n");
            sb.append(" - 상담사: ").append(t.botMessage() == null ? "" : t.botMessage()).append("\n");
        }
        return sb.toString();
    }
}
