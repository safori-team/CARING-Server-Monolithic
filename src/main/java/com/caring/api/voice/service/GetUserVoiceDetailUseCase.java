package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.exception.VoiceHandler;
import com.caring.api.voice.dto.VoiceDetailResponse;
import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class GetUserVoiceDetailUseCase {
    private final VoiceAdaptor voiceAdaptor;
    public VoiceDetailResponse execute(Long voiceId, String username) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        if(!voice.getUser().getUsername().equals(username))throw VoiceHandler.NO_PERMISSION;
        // TODO fill out fields
        return VoiceDetailResponse.builder()
                .title(voice.getVoiceTitle())
                .voiceId(voiceId)
                .s3Url(voice.getVoiceKey())
                .createdAt(voice.getCreatedDate())
                .build();
    }
}
