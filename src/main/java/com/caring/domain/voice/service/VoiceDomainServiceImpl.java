package com.caring.domain.voice.service;

import com.caring.common.annotation.DomainService;
import com.caring.domain.question.entity.QuestionCategory;
import com.caring.domain.question.entity.VoiceQuestion;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.repository.VoiceQuestionRepository;
import com.caring.domain.voice.repository.VoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@DomainService
@Transactional
@RequiredArgsConstructor
public class VoiceDomainServiceImpl implements VoiceDomainService {

    private final VoiceRepository voiceRepository;
    private final VoiceQuestionRepository voiceQuestionRepository;

    @Override
    public Voice uploadVoiceFile(User user, String s3URL) {
        Voice voice = Voice.builder()
                .user(user)
                .voiceKey(s3URL)
                .voiceTitle("voiceTitle")       // TODO voice Title
                .build();
        return voiceRepository.save(voice);
    }

    @Override
    public VoiceQuestion linkVoiceQuestion(Voice voice, QuestionCategory questionCategory, int questionIndex) {
        VoiceQuestion voiceQuestion = VoiceQuestion.builder()
                .voice(voice)
                .questionCategory(questionCategory)
                .questionIndex(questionIndex)
                .build();
        return voiceQuestionRepository.save(voiceQuestion);
    }
}
