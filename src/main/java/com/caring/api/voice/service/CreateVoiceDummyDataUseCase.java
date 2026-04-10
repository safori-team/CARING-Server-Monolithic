package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.domain.emotion.entity.EmotionType;
import com.caring.domain.question.entity.QuestionCategory;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceAnalyze;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.domain.voice.entity.VoiceContent;
import com.caring.domain.voice.repository.VoiceAnalyzeRepository;
import com.caring.domain.voice.repository.VoiceCompositeRepository;
import com.caring.domain.voice.repository.VoiceContentRepository;
import com.caring.domain.voice.service.VoiceDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@UseCase
@Transactional
@RequiredArgsConstructor
public class CreateVoiceDummyDataUseCase {

    private static final int DUMMY_COUNT = 5;

    private final UserAdaptor userAdaptor;
    private final VoiceDomainService voiceDomainService;
    private final VoiceAnalyzeRepository voiceAnalyzeRepository;
    private final VoiceContentRepository voiceContentRepository;
    private final VoiceCompositeRepository voiceCompositeRepository;

    public List<Long> execute(String username) {
        User user = userAdaptor.queryUserByUsername(username);
        List<Long> voiceIds = new ArrayList<>(DUMMY_COUNT);

        for (int i = 0; i < DUMMY_COUNT; i++) {
            QuestionCategory category = QuestionCategory.values()[i % QuestionCategory.values().length];
            EmotionType topEmotion = EmotionType.values()[i % EmotionType.values().length];

            Voice voice = voiceDomainService.uploadVoiceFile(
                    user,
                    "https://dummy-bucket.s3.ap-northeast-2.amazonaws.com/voices/" + username + "/dummy-" + (i + 1) + ".m4a"
            );
            voiceDomainService.linkVoiceQuestion(voice, category, i);

            voiceContentRepository.save(VoiceContent.builder()
                    .voice(voice)
                    .content("Test transcript " + (i + 1) + " for " + category.name())
                    .scoreBps((short) (150 + i * 80))
                    .magnitudeX1000(900 + i * 120)
                    .locale("ko-KR")
                    .provider("dummy")
                    .modelVersion("test-v1")
                    .confidenceBps((short) (800 + i * 20))
                    .build());

            voiceAnalyzeRepository.save(VoiceAnalyze.builder()
                    .voice(voice)
                    .happyBps(emotionScore(EmotionType.HAPPY, topEmotion))
                    .sadBps(emotionScore(EmotionType.SAD, topEmotion))
                    .neutralBps(emotionScore(EmotionType.NEUTRAL, topEmotion))
                    .angryBps(emotionScore(EmotionType.ANGRY, topEmotion))
                    .fearBps(emotionScore(EmotionType.FEAR, topEmotion))
                    .surpriseBps(emotionScore(EmotionType.SURPRISE, topEmotion))
                    .topEmotion(topEmotion.name())
                    .topConfidenceBps(820 + i * 20)
                    .modelVersion("test-v1")
                    .build());

            voiceCompositeRepository.save(VoiceComposite.builder()
                    .voice(voice)
                    .textScoreBps(100 + i * 40)
                    .textMagnitudeX1000(1000 + i * 100)
                    .alphaBps(220 + i * 30)
                    .betaBps(280 + i * 30)
                    .valenceX1000(450 + i * 70)
                    .arousalX1000(500 + i * 60)
                    .intensityX1000(550 + i * 50)
                    .happyBps(emotionScore(EmotionType.HAPPY, topEmotion))
                    .sadBps(emotionScore(EmotionType.SAD, topEmotion))
                    .neutralBps(emotionScore(EmotionType.NEUTRAL, topEmotion))
                    .angryBps(emotionScore(EmotionType.ANGRY, topEmotion))
                    .fearBps(emotionScore(EmotionType.FEAR, topEmotion))
                    .surpriseBps(emotionScore(EmotionType.SURPRISE, topEmotion))
                    .topEmotion(topEmotion)
                    .topEmotionConfidenceBps(850 + i * 15)
                    .build());

            voiceIds.add(voice.getId());
        }

        return voiceIds;
    }

    private int emotionScore(EmotionType candidate, EmotionType topEmotion) {
        return candidate == topEmotion ? 7000 : 600;
    }
}
