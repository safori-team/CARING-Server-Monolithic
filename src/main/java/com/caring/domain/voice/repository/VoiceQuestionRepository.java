package com.caring.domain.voice.repository;

import com.caring.domain.question.entity.VoiceQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceQuestionRepository extends JpaRepository<VoiceQuestion, Long> {
}
