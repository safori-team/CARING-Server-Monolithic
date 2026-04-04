package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.consts.UserServiceQuestionStaticValues;
import com.caring.common.service.S3PresignService;
import com.caring.domain.question.entity.QuestionCategory;
import com.caring.domain.question.exception.QuestionHandler;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.service.VoiceDomainService;
import com.caring.domain.voice.entity.Voice;
import com.caring.infra.ai.hume.scheduler.DiaryBatchItem;
import com.caring.infra.ai.hume.scheduler.HumeBatchScheduler;

import java.util.List;
import java.util.Optional;

@UseCase
public class UploadVoiceFileUseCase {

    private final UserAdaptor userAdaptor;
    private final VoiceDomainService voiceDomainService;
    private final HumeBatchScheduler humeBatchScheduler;
    private final Optional<S3PresignService> s3PresignService;

    public UploadVoiceFileUseCase(UserAdaptor userAdaptor,
                                  VoiceDomainService voiceDomainService,
                                  HumeBatchScheduler humeBatchScheduler,
                                  Optional<S3PresignService> s3PresignService) {
        this.userAdaptor = userAdaptor;
        this.voiceDomainService = voiceDomainService;
        this.humeBatchScheduler = humeBatchScheduler;
        this.s3PresignService = s3PresignService;
    }

    /**
     * @param voiceKey S3 오브젝트 키 (예: voices/user1/uuid.m4a)
     */
    public Long execute(String username, QuestionCategory questionCategory, int questionIndex,
                        String voiceKey, String recordedAt) {
        validateQuestion(questionCategory, questionIndex);
        User user = userAdaptor.queryUserByUsername(username);
        Voice voice = voiceDomainService.uploadVoiceFile(user, voiceKey);
        voiceDomainService.linkVoiceQuestion(voice, questionCategory, questionIndex);

        String questionText = UserServiceQuestionStaticValues.QUESTION_MAP
                .get(questionCategory.name()).get(questionIndex);

        // Hume AI는 직접 접근 가능한 URL이 필요 — presigned GET URL 생성 (유효 1시간)
        String humeAccessUrl = s3PresignService
                .map(svc -> svc.generateGetUrl(voiceKey))
                .orElse(voiceKey);

        humeBatchScheduler.enqueue(DiaryBatchItem.builder()
                .userId(user.getUserUuid())
                .userName(user.getName())
                .question(questionText)
                .s3Url(humeAccessUrl)
                .recordedAt(recordedAt)
                .build());

        return voice.getId();
    }

    private void validateQuestion(QuestionCategory questionCategory, int questionIndex) {
        if (questionCategory == null) {
            throw QuestionHandler.NOT_FOUND;
        }
        List<String> questions = UserServiceQuestionStaticValues.QUESTION_MAP.get(questionCategory.name());
        if (questions == null || questionIndex < 0 || questionIndex >= questions.size()) {
            throw QuestionHandler.NOT_FOUND;
        }
    }
}
