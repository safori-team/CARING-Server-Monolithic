package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.service.S3PresignService;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.api.voice.dto.VoiceListItem;
import com.caring.api.voice.dto.VoiceListResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@UseCase
public class GetUserVoiceListUseCase {

    private final VoiceAdaptor voiceAdaptor;
    private final Optional<S3PresignService> s3PresignService;

    public GetUserVoiceListUseCase(VoiceAdaptor voiceAdaptor, Optional<S3PresignService> s3PresignService) {
        this.voiceAdaptor = voiceAdaptor;
        this.s3PresignService = s3PresignService;
    }

    public VoiceListResponse execute(String username, String date) {
        List<Voice> voices = voiceAdaptor.queryByUsernameAndCreatedAt(username, LocalDate.parse(date));
        return toResponse(voices);
    }

    public VoiceListResponse execute(String username) {
        List<Voice> voices = voiceAdaptor.queryByUsername(username);
        //TODO use querydsl
        return toResponse(voices);
    }

    private VoiceListResponse toResponse(List<Voice> voices) {
        List<VoiceListItem> voiceListItemList = voices.stream()
                .map(v -> VoiceListItem.builder()
                        .voiceId(v.getId())
                        .createdAt(v.getCreatedDate().toLocalDate())
                        .s3Url(resolveUrl(v.getVoiceKey()))
                        .build())
                .collect(Collectors.toList());
        return VoiceListResponse.builder()
                .voices(voiceListItemList)
                .build();
    }

    private String resolveUrl(String voiceKey) {
        return s3PresignService
                .map(svc -> svc.generateGetUrl(voiceKey))
                .orElse(null);
    }
}
