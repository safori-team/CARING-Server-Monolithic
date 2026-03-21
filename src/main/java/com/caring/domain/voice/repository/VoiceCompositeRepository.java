package com.caring.domain.voice.repository;

import com.caring.domain.voice.entity.VoiceComposite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface VoiceCompositeRepository extends JpaRepository<VoiceComposite, Long> {

    List<VoiceComposite> findByVoice_User_UsernameAndCreatedDateBetween(
            String username,
            LocalDateTime start,
            LocalDateTime end
    );
}
