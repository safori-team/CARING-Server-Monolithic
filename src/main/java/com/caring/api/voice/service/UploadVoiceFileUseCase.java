package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.consts.UserServiceQuestionStaticValues;
import com.caring.domain.question.entity.QuestionCategory;
import com.caring.domain.question.exception.QuestionHandler;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.service.VoiceDomainService;
import com.caring.domain.voice.entity.Voice;
import com.caring.infra.ai.hume.scheduler.DiaryBatchItem;
import com.caring.infra.ai.hume.scheduler.HumeBatchScheduler;
import lombok.RequiredArgsConstructor;

import java.util.List;

@UseCase
@RequiredArgsConstructor
public class UploadVoiceFileUseCase {

    private final UserAdaptor userAdaptor;
    private final VoiceDomainService voiceDomainService;
    private final HumeBatchScheduler humeBatchScheduler;

    public Long execute(String username, QuestionCategory questionCategory, int questionIndex,
                        String bucketUrl, String recordedAt) {
        validateQuestion(questionCategory, questionIndex);
        User user = userAdaptor.queryUserByUsername(username);
        Voice voice = voiceDomainService.uploadVoiceFile(user, bucketUrl);
        voiceDomainService.linkVoiceQuestion(voice, questionCategory, questionIndex);

        String questionText = UserServiceQuestionStaticValues.QUESTION_MAP
                .get(questionCategory.name()).get(questionIndex);
        humeBatchScheduler.enqueue(DiaryBatchItem.builder()
                .userId(user.getUserUuid())
                .userName(user.getName())
                .question(questionText)
                .s3Url(bucketUrl)
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
