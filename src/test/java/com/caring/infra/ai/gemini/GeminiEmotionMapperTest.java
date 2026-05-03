package com.caring.infra.ai.gemini;

import com.caring.domain.emotion.entity.EmotionType;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.infra.ai.gemini.dto.GeminiAnalysisResult;
import com.caring.infra.ai.gemini.dto.GeminiEmotionScore;
import com.caring.infra.ai.gemini.dto.GeminiSegment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

// Gemini responseSchema 강제 구조:
// emotions → [{ name, intensity }] 배열, category → segment 단위
class GeminiEmotionMapperTest {

    private GeminiEmotionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new GeminiEmotionMapper();
    }

    @Test
    @DisplayName("단일 세그먼트 happy 감정 → HAPPY topEmotion, bps 합계 10000")
    void toVoiceComposite_singleHappySegment() {
        GeminiAnalysisResult result = new GeminiAnalysisResult(
                "오늘 기분이 좋아요",
                "긍정적인 발화",
                List.of(new GeminiSegment(
                        "00:00",
                        "오늘 기분이 좋아요",
                        "happy",
                        List.of(
                                new GeminiEmotionScore("joy", 0.8),
                                new GeminiEmotionScore("contentment", 0.6)
                        ),
                        "밝고 경쾌한 톤"
                )),
                8.0
        );
        Voice voice = mock(Voice.class);

        VoiceComposite composite = mapper.toVoiceComposite(result, voice);

        assertThat(composite.getTopEmotion()).isEqualTo(EmotionType.HAPPY);
        int total = composite.getHappyBps() + composite.getSadBps() + composite.getNeutralBps()
                + composite.getAngryBps() + composite.getFearBps() + composite.getSurpriseBps();
        assertThat(total).isEqualTo(10000);
        assertThat(composite.getHappyBps()).isEqualTo(10000);
    }

    @Test
    @DisplayName("fear category → FEAR topEmotion, fearBps=10000")
    void toVoiceComposite_fearCategory_mapsToFear() {
        GeminiAnalysisResult result = new GeminiAnalysisResult(
                "무서워요",
                "두려운 발화",
                List.of(new GeminiSegment(
                        "00:00",
                        "무서워요",
                        "fear",
                        List.of(
                                new GeminiEmotionScore("fear", 1.0),
                                new GeminiEmotionScore("anxiety", 0.5)
                        ),
                        "떨리는 목소리"
                )),
                3.0
        );
        Voice voice = mock(Voice.class);

        VoiceComposite composite = mapper.toVoiceComposite(result, voice);

        assertThat(composite.getTopEmotion()).isEqualTo(EmotionType.FEAR);
        assertThat(composite.getFearBps()).isEqualTo(10000);
    }

    @Test
    @DisplayName("surprise category → SURPRISE topEmotion, surpriseBps=10000")
    void toVoiceComposite_surpriseCategory_mapsToSurprise() {
        GeminiAnalysisResult result = new GeminiAnalysisResult(
                "와 정말요?",
                "놀라운 발화",
                List.of(new GeminiSegment(
                        "00:00",
                        "와 정말요?",
                        "surprise",
                        List.of(
                                new GeminiEmotionScore("surprise_positive", 0.9),
                                new GeminiEmotionScore("awe", 0.4)
                        ),
                        "급격히 높아지는 톤"
                )),
                6.0
        );
        Voice voice = mock(Voice.class);

        VoiceComposite composite = mapper.toVoiceComposite(result, voice);

        assertThat(composite.getTopEmotion()).isEqualTo(EmotionType.SURPRISE);
        assertThat(composite.getSurpriseBps()).isEqualTo(10000);
    }

    @Test
    @DisplayName("복수 세그먼트 혼합 → bps 합계 정확히 10000")
    void toVoiceComposite_multipleSegments_totalBpsIs10000() {
        GeminiAnalysisResult result = new GeminiAnalysisResult(
                "오늘은 슬프고 무서웠어요",
                "복합 감정",
                List.of(
                        new GeminiSegment("00:00", "오늘은 슬퍼요", "sad",
                                List.of(new GeminiEmotionScore("sadness", 0.7)), "낮은 톤"),
                        new GeminiSegment("00:05", "무서워요", "fear",
                                List.of(new GeminiEmotionScore("fear", 0.3)), "떨림")
                ),
                4.0
        );
        Voice voice = mock(Voice.class);

        VoiceComposite composite = mapper.toVoiceComposite(result, voice);

        int total = composite.getHappyBps() + composite.getSadBps() + composite.getNeutralBps()
                + composite.getAngryBps() + composite.getFearBps() + composite.getSurpriseBps();
        assertThat(total).isEqualTo(10000);
    }

    @Test
    @DisplayName("valence는 HAPPY(+0.80), NEUTRAL(0.00) 혼합 시 양수")
    void toVoiceComposite_happyAndNeutral_positiveValence() {
        GeminiAnalysisResult result = new GeminiAnalysisResult(
                "그냥저냥이에요",
                "평온",
                List.of(new GeminiSegment(
                        "00:00",
                        "그냥저냥이에요",
                        "happy",
                        List.of(
                                new GeminiEmotionScore("joy", 0.5),
                                new GeminiEmotionScore("calmness", 0.5)
                        ),
                        "평온한 톤"
                )),
                7.0
        );
        Voice voice = mock(Voice.class);

        VoiceComposite composite = mapper.toVoiceComposite(result, voice);

        assertThat(composite.getValenceX1000()).isGreaterThan(0);
        assertThat(composite.getIntensityX1000()).isGreaterThan(0);
    }
}
