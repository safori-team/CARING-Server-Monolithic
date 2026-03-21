package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.api.voice.dto.VoiceListItem;
import com.caring.api.voice.dto.VoiceListResponse;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@UseCase
@RequiredArgsConstructor
public class GetUserVoiceListUseCase {
    private final VoiceAdaptor voiceAdaptor;


    public VoiceListResponse execute(String username, String date) {
        List<Voice> voices = voiceAdaptor.queryByUsernameAndCreatedAt(username, LocalDate.parse(date));
        List<VoiceListItem> voiceListItemList = voices.stream()
                .map(v -> VoiceListItem.builder()
                        .voiceId(v.getId())
                        .createdAt(v.getCreatedDate().toLocalDate())
                        .s3Url(v.getVoiceKey())
                        .build()).collect(Collectors.toList());
        return VoiceListResponse.builder()
                .voices(voiceListItemList)
                .build();
    }

    public VoiceListResponse execute(String username) {
        List<Voice> voices = voiceAdaptor.queryByUsername(username);
        //TODO use querydsl
        List<VoiceListItem> voiceListItemList = voices.stream()
                .map(v -> VoiceListItem.builder()
                        .voiceId(v.getId())
                        .createdAt(v.getCreatedDate().toLocalDate())
                        .s3Url(v.getVoiceKey())
                        .build()).collect(Collectors.toList());
        return VoiceListResponse.builder()
                .voices(voiceListItemList)
                .build();
    }
}
