package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.consts.UserServiceQuestionStaticValues;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceContent;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.domain.voice.exception.VoiceHandler;
import com.caring.domain.question.entity.VoiceQuestion;
import com.caring.domain.voice.repository.VoiceContentRepository;
import com.caring.domain.voice.repository.VoiceQuestionRepository;
import com.caring.api.voice.dto.VoiceDetailResponse;
import lombok.RequiredArgsConstructor;

import java.util.List;

@UseCase
@RequiredArgsConstructor
public class GetUserVoiceDetailUseCase {
    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceQuestionRepository voiceQuestionRepository;
    private final VoiceContentRepository voiceContentRepository;

    public VoiceDetailResponse execute(Long voiceId, String username) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        if(!voice.getUser().getUsername().equals(username))throw VoiceHandler.NO_PERMISSION;

        VoiceComposite composite = voiceCompositeAdaptor.queryByVoiceIds(List.of(voiceId)).stream()
                .findFirst()
                .orElse(null);
        VoiceQuestion voiceQuestion = voiceQuestionRepository.findByVoice_Id(voiceId).orElse(null);
        VoiceContent voiceContent = voiceContentRepository.findByVoice_Id(voiceId).orElse(null);

        return VoiceDetailResponse.builder()
                .voiceId(voiceId)
                .createdAt(voice.getCreatedDate().toLocalDate())
                .topEmotion(composite != null ? composite.getTopEmotion() : null)
                .questionTitle(resolveQuestionTitle(voiceQuestion))
                .content(voiceContent != null ? voiceContent.getContent() : null)
                .s3Url(voice.getVoiceKey())
                .build();
    }

    private String resolveQuestionTitle(VoiceQuestion voiceQuestion) {
        if (voiceQuestion == null) {
            return null;
        }

        List<String> questions = UserServiceQuestionStaticValues.QUESTION_MAP.get(voiceQuestion.getQuestionCategory().name());
        if (questions == null || voiceQuestion.getQuestionIndex() < 0 || voiceQuestion.getQuestionIndex() >= questions.size()) {
            return null;
        }
        return questions.get(voiceQuestion.getQuestionIndex());
    }
}
