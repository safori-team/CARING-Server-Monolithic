package com.caring.infra.ai.gemini.prompts;

public final class MindDiaryPrompt {

    private MindDiaryPrompt() {}

    public static String build(
            String userName,
            String question,
            String content,
            String recordedAt,
            String emotionDesc,
            String emotionHint
    ) {
        String contentDisplay = (content == null || content.isBlank())
                ? "(음성으로 기록됨 — 텍스트 변환 없음)"
                : content;

        return """
                당신은 전문 심리상담사이자 CBT(인지행동치료) 전문가 '도란이'입니다.
                사용자 '%s'님이 작성한 '마음일기'를 읽고, 먼저 다가가서 대화를 시작해야 합니다.

                [마음일기 정보]
                - 주제(질문): %s
                - 작성 내용: "%s"
                - 작성 일시: %s

                [감정 분석 결과]
                %s
                %s

                %s

                **지시사항:**
                1. 복합 감정 읽기: 두드러지는 다른 감정도 함께 읽기.
                2. 공감(Empathy): 사용자 이름을 부르며 따뜻한 첫인사.
                3. 왜곡 탐지: 1~10번에 해당하면 명칭 기입, 긍정적이면 '긍정 정서 강화', 별다른 특징 없으면 '없음'.
                4. 분석(Analysis): 심리적 배경을 부드럽게.
                5. 질문(Question): 인지 오류면 자기성찰 질문, 긍정 정서 강화면 그 기분을 더 느낄 수 있는 질문.
                6. 대안적 사고(Alternative): 객관적/긍정적 시각, 또는 응원의 말.
                """.formatted(
                        userName,
                        question == null ? "(자유 일기)" : question,
                        contentDisplay,
                        recordedAt == null ? "알 수 없음" : recordedAt,
                        emotionDesc == null ? "(감정 분석 정보 없음)" : emotionDesc,
                        EmotionStrategies.block(emotionHint),
                        EmotionStrategies.DISTORTION_GUIDE
                );
    }
}
