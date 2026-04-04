package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.consts.UserServiceQuestionStaticValues;
import com.caring.common.service.S3PresignService;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.repository.VoiceQuestionRepository;
import com.caring.infra.ai.hume.scheduler.DiaryBatchItem;
import com.caring.infra.ai.hume.scheduler.HumeBatchScheduler;

import java.util.List;
import java.util.Optional;

@UseCase
public class TriggerHumeAnalyzeUseCase {

    private final VoiceAdaptor voiceAdaptor;
    private final VoiceQuestionRepository voiceQuestionRepository;
    private final HumeBatchScheduler humeBatchScheduler;
    private final Optional<S3PresignService> s3PresignService;

    public TriggerHumeAnalyzeUseCase(VoiceAdaptor voiceAdaptor,
                                     VoiceQuestionRepository voiceQuestionRepository,
                                     HumeBatchScheduler humeBatchScheduler,
                                     Optional<S3PresignService> s3PresignService) {
        this.voiceAdaptor = voiceAdaptor;
        this.voiceQuestionRepository = voiceQuestionRepository;
        this.humeBatchScheduler = humeBatchScheduler;
        this.s3PresignService = s3PresignService;
    }

    /**
     * 특정 voiceId를 즉시 Hume AI에 분석 요청한다. (테스트/디버그용)
     *
     * @return Hume jobId
     */
    public String execute(Long voiceId) {
        Voice voice = voiceAdaptor.queryById(voiceId);

        String questionText = resolveQuestion(voiceId);
        String humeAccessUrl = resolveHumeUrl(voice.getVoiceKey());

        DiaryBatchItem item = DiaryBatchItem.builder()
                .userId(voice.getUser().getUserUuid())
                .userName(voice.getUser().getName())
                .question(questionText)
                .s3Url(humeAccessUrl)
                .recordedAt(voice.getCreatedDate().toString())
                .build();

        return humeBatchScheduler.triggerNow(item);
    }

    private String resolveQuestion(Long voiceId) {
        return voiceQuestionRepository.findByVoice_Id(voiceId)
                .map(vq -> {
                    List<String> questions = UserServiceQuestionStaticValues.QUESTION_MAP
                            .get(vq.getQuestionCategory().name());
                    if (questions != null && vq.getQuestionIndex() < questions.size()) {
                        return questions.get(vq.getQuestionIndex());
                    }
                    return "";
                })
                .orElse("");
    }

    private String resolveHumeUrl(String voiceKey) {
        return s3PresignService
                .map(svc -> svc.generateGetUrl(voiceKey))
                .orElse(voiceKey);
    }
}
