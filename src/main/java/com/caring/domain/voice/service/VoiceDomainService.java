package com.caring.domain.voice.service;

import com.caring.domain.question.entity.QuestionCategory;
import com.caring.domain.question.entity.VoiceQuestion;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.entity.Voice;

public interface VoiceDomainService {

    Voice uploadVoiceFile(User user, String s3URL);
    VoiceQuestion linkVoiceQuestion(Voice voice, QuestionCategory questionCategory, int questionIndex);
}
