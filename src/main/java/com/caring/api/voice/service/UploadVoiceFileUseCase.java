package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.domain.question.entity.QuestionCategory;
import com.caring.domain.question.exception.QuestionHandler;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.service.VoiceDomainService;
import com.caring.infra.ai.gemini.GeminiVoiceAnalyzer;

import java.util.List;

@UseCase
public class UploadVoiceFileUseCase {

    private final UserAdaptor userAdaptor;
    private final VoiceDomainService voiceDomainService;
    private final GeminiVoiceAnalyzer geminiVoiceAnalyzer;

    public UploadVoiceFileUseCase(UserAdaptor userAdaptor,
                                  VoiceDomainService voiceDomainService,
                                  GeminiVoiceAnalyzer geminiVoiceAnalyzer) {
        this.userAdaptor = userAdaptor;
        this.voiceDomainService = voiceDomainService;
        this.geminiVoiceAnalyzer = geminiVoiceAnalyzer;
    }

    public Long execute(String username, QuestionCategory questionCategory, int questionIndex,
                        String voiceKey) {
        validateQuestion(questionCategory, questionIndex);
        User user = userAdaptor.queryUserByUsername(username);
        Voice voice = voiceDomainService.uploadVoiceFile(user, voiceKey);
        voiceDomainService.linkVoiceQuestion(voice, questionCategory, questionIndex);

        geminiVoiceAnalyzer.analyzeAsync(voice.getId(), voiceKey);

        return voice.getId();
    }

    private void validateQuestion(QuestionCategory questionCategory, int questionIndex) {
        if (questionCategory == null) {
            throw QuestionHandler.NOT_FOUND;
        }
        List<String> questions = com.caring.common.consts.UserServiceQuestionStaticValues.QUESTION_MAP.get(questionCategory.name());
        if (questions == null || questionIndex < 0 || questionIndex >= questions.size()) {
            throw QuestionHandler.NOT_FOUND;
        }
    }
}
