package com.caring.domain.voice.repository;

import com.caring.domain.voice.entity.Voice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface VoiceRepository extends JpaRepository<Voice, Long> {
    List<Voice> findByUser_Username(String username);

    List<Voice> findByUser_UsernameAndCreatedDateBetween(String username,
                                                         LocalDateTime start,
                                                         LocalDateTime end);
}
