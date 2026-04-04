package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.service.S3PresignService;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.exception.VoiceHandler;
import com.caring.api.voice.dto.VoiceDetailResponse;

import java.util.Optional;

@UseCase
public class GetUserVoiceDetailUseCase {

    private final VoiceAdaptor voiceAdaptor;
    private final Optional<S3PresignService> s3PresignService;

    public GetUserVoiceDetailUseCase(VoiceAdaptor voiceAdaptor, Optional<S3PresignService> s3PresignService) {
        this.voiceAdaptor = voiceAdaptor;
        this.s3PresignService = s3PresignService;
    }

    public VoiceDetailResponse execute(Long voiceId, String username) {
        Voice voice = voiceAdaptor.queryById(voiceId);
        if (!voice.getUser().getUsername().equals(username)) throw VoiceHandler.NO_PERMISSION;

        String s3Url = s3PresignService
                .map(svc -> svc.generateGetUrl(voice.getVoiceKey()))
                .orElse(voice.getVoiceKey());

        // TODO fill out fields
        return VoiceDetailResponse.builder()
                .title(voice.getVoiceTitle())
                .voiceId(voiceId)
                .s3Url(s3Url)
                .createdAt(voice.getCreatedDate())
                .build();
    }
}
