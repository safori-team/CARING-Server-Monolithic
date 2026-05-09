package com.caring.infra.ai.gemini.prompts;

import java.util.Map;

public final class EmotionStrategies {

    private EmotionStrategies() {}

    private static final Map<String, String> STRATEGY = Map.of(
            "happy",
            "[전략] 기쁨을 더 생생하게 느낄 수 있도록 그 감정의 구체적인 원인과 의미를 함께 탐색하세요.",
            "sad",
            "[전략] 슬픔을 충분히 인정하고 공감한 뒤, 자신을 향한 따뜻한 시선을 회복할 수 있도록 도우세요.",
            "neutral",
            "[전략] 표면 아래 감정이 숨어 있을 수 있으니, 행간과 맥락에서 미묘한 감정을 읽어주세요.",
            "angry",
            "[전략] 분노 뒤에 숨은 좌절·상처를 짚어주고, 비난이 아닌 공감으로 상황을 재구성하세요.",
            "anxiety",
            "[전략] 불안의 대상을 구체화해서 다룰 수 있는 작은 단위로 나누어 보도록 안내하세요.",
            "surprise",
            "[전략] 놀람의 원인을 짚고, 긍정/부정 어느 쪽으로 흘러가는 감정인지 확인하세요."
    );

    public static String block(String emotionEn) {
        if (emotionEn == null) return "";
        String s = STRATEGY.get(emotionEn.toLowerCase());
        return s == null ? "" : "\n" + s + "\n";
    }

    public static String block(String primaryEn, String secondaryEn) {
        StringBuilder sb = new StringBuilder();
        if (primaryEn != null) sb.append(block(primaryEn));
        if (secondaryEn != null) sb.append(block(secondaryEn));
        return sb.toString();
    }

    public static final String DISTORTION_GUIDE = """
            [상담사 분석 가이드라인 (CBT 기반)]
            1. 흑백사고: 모든 것을 '성공 아니면 실패'로만 보는 이분법적 사고.
            2. 선택적 추상: 긍정적인 면은 무시하고 사소한 부정적 세부 사항에만 집착하는 것.
            3. 자의적 추론: 증거 없이 상황을 부정적으로 해석하는 것 (독심술, 점쟁이 오류).
            4. 과잉일반화: 한 번의 실수를 영원한 실패로 간주하는 것.
            5. 확대/축소: 자신의 실수는 크게 부풀리고, 장점은 의미 없게 축소하는 것.
            6. 개인화: 자신과 무관한 외부 사건을 자신의 탓으로 돌리는 것.
            7. 정서적 추론: "내가 그렇게 느끼니까 그건 사실이야"라고 믿는 것.
            8. 긍정 격하: 칭찬이나 성취를 "운이 좋았을 뿐"이라며 가치를 깎아내리는 것.
            9. 파국화: 미래에 일어날 일을 끔찍한 재앙으로 미리 단정 짓는 것.
            10. 잘못된 별칭 붙이기: 실수한 자신에게 "나는 패배자야"라고 꼬리표를 붙이는 것.
            11. 긍정 정서 강화: 인지 오류가 없고, 내담자가 통찰을 얻었거나 안정을 찾은 상태.
            """;
}
