package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.consts.UserServiceQuestionStaticValues;
import com.caring.domain.question.entity.VoiceQuestion;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.domain.voice.entity.VoiceContent;
import com.caring.domain.voice.repository.VoiceContentRepository;
import com.caring.domain.voice.repository.VoiceQuestionRepository;
import com.caring.api.voice.dto.VoiceListItem;
import com.caring.api.voice.dto.VoiceListResponse;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UseCase
@RequiredArgsConstructor
public class GetUserVoiceListUseCase {
    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceQuestionRepository voiceQuestionRepository;
    private final VoiceContentRepository voiceContentRepository;


    public VoiceListResponse execute(String username, String date) {
        List<Voice> voices = voiceAdaptor.queryByUsernameAndCreatedAt(username, LocalDate.parse(date));
        return VoiceListResponse.builder()
                .voices(toVoiceListItems(voices))
                .build();
    }

    public VoiceListResponse execute(String username) {
        List<Voice> voices = voiceAdaptor.queryByUsername(username);
        return VoiceListResponse.builder()
                .voices(toVoiceListItems(voices))
                .build();
    }

    //TODO optimization
    private List<VoiceListItem> toVoiceListItems(List<Voice> voices) {
        List<Long> voiceIds = voices.stream().map(Voice::getId).toList();

        Map<Long, VoiceComposite> compositeByVoiceId = voiceCompositeAdaptor.queryByVoiceIds(voiceIds).stream()
                .collect(Collectors.toMap(vc -> vc.getVoice().getId(), vc -> vc));
        Map<Long, VoiceQuestion> questionByVoiceId = voiceQuestionRepository.findByVoice_IdIn(voiceIds).stream()
                .collect(Collectors.toMap(vq -> vq.getVoice().getId(), vq -> vq));
        Map<Long, VoiceContent> contentByVoiceId = voiceContentRepository.findByVoice_IdIn(voiceIds).stream()
                .collect(Collectors.toMap(vc -> vc.getVoice().getId(), vc -> vc));

        return voices.stream()
                .map(v -> VoiceListItem.builder()
                        .voiceId(v.getId())
                        .createdAt(v.getCreatedDate().toLocalDate())
                        .emotion(compositeByVoiceId.containsKey(v.getId())
                                ? compositeByVoiceId.get(v.getId()).getTopEmotion()
                                : null)
                        .questionTitle(resolveQuestionTitle(questionByVoiceId.get(v.getId())))
                        .content(contentByVoiceId.containsKey(v.getId())
                                ? contentByVoiceId.get(v.getId()).getContent()
                                : null)
                        .s3Url(v.getVoiceKey())
                        .build())
                .collect(Collectors.toList());
    }

    private String resolveQuestionTitle(VoiceQuestion voiceQuestion) {
        if (voiceQuestion == null) {
            return null;
        }

        List<String> questions = UserServiceQuestionStaticValues.QUESTION_MAP.get(voiceQuestion.getQuestionCategory().name());
        if (questions == null || voiceQuestion.getQuestionIndex() < 0 || voiceQuestion.getQuestionIndex() >= questions.size()) {
            return null;
        }
        return questions.get(voiceQuestion.getQuestionIndex());
    }
}
