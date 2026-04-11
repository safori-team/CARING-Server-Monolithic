package com.caring.domain.voice.repository;

import com.caring.domain.question.entity.VoiceQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VoiceQuestionRepository extends JpaRepository<VoiceQuestion, Long> {
    Optional<VoiceQuestion> findByVoice_Id(Long voiceId);

    List<VoiceQuestion> findByVoice_IdIn(List<Long> voiceIds);
}
