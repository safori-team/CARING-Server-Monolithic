package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.consts.UserServiceQuestionStaticValues;
import com.caring.domain.question.entity.QuestionCategory;
import com.caring.domain.question.exception.QuestionHandler;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.service.VoiceDomainService;
import com.caring.domain.voice.entity.Voice;
import com.caring.infra.ai.AiServerClient;
import lombok.RequiredArgsConstructor;

import java.util.List;

@UseCase
@RequiredArgsConstructor
public class UploadVoiceFileUseCase {

    private final UserAdaptor userAdaptor;
    private final VoiceDomainService voiceDomainService;
    private final AiServerClient aiServerClient;

    public Long execute(String username, QuestionCategory questionCategory, int questionIndex, String bucketUrl) {
        validateQuestion(questionCategory, questionIndex);
        User user = userAdaptor.queryUserByUsername(username);
        Voice voice = voiceDomainService.uploadVoiceFile(user, bucketUrl);
        voiceDomainService.linkVoiceQuestion(voice, questionCategory, questionIndex);
        aiServerClient.sendVoiceForAnalysis(bucketUrl, voice.getId());
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
