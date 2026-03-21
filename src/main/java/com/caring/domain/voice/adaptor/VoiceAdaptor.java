package com.caring.domain.voice.adaptor;

import com.caring.domain.voice.entity.Voice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface VoiceAdaptor {

    Voice queryById(Long voiceId);
    List<Voice> queryByUsername(String username);
    List<Voice> queryByUsernameAndCreatedAt(String username, LocalDate createdAt);
}
