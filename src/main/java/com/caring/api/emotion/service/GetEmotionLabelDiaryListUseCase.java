package com.caring.api.emotion.service;

import com.caring.api.emotion.dto.EmotionLabelDiaryListResponse;
import com.caring.api.voice.dto.VoiceListItem;
import com.caring.common.annotation.UseCase;
import com.caring.common.consts.UserServiceQuestionStaticValues;
import com.caring.domain.question.entity.VoiceQuestion;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.domain.voice.entity.VoiceContent;
import com.caring.domain.voice.repository.VoiceContentRepository;
import com.caring.domain.voice.repository.VoiceQuestionRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 특정 월에 특정 세부 감정을 느꼈던 일기 목록 조회.
 * GET /v1/api/users/voices/analyzing/bubble/diaries?yearMonth=2024-01&label=joy
 */
@UseCase
public class GetEmotionLabelDiaryListUseCase {

    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceQuestionRepository voiceQuestionRepository;
    private final VoiceContentRepository voiceContentRepository;

    public GetEmotionLabelDiaryListUseCase(
            VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor,
            VoiceCompositeAdaptor voiceCompositeAdaptor,
            VoiceQuestionRepository voiceQuestionRepository,
            VoiceContentRepository voiceContentRepository) {
        this.voiceEmotionLabelAdaptor = voiceEmotionLabelAdaptor;
        this.voiceCompositeAdaptor = voiceCompositeAdaptor;
        this.voiceQuestionRepository = voiceQuestionRepository;
        this.voiceContentRepository = voiceContentRepository;
    }

    public EmotionLabelDiaryListResponse execute(String username, String yearMonth, String label) {
        YearMonth ym = YearMonth.parse(yearMonth);
        List<Voice> voices = voiceEmotionLabelAdaptor.findVoicesByLabel(
                username, label, ym.getYear(), ym.getMonthValue());

        // category 조회 — Adaptor를 통해 첫 번째 Voice의 레이블에서 추출
        String category = voices.isEmpty() ? null :
                voiceEmotionLabelAdaptor.findByVoiceId(voices.get(0).getId()).stream()
                        .filter(vel -> label.equals(vel.getLabel()))
                        .map(vel -> vel.getCategory())
                        .findFirst()
                        .orElse(null);

        return EmotionLabelDiaryListResponse.builder()
                .yearMonth(yearMonth)
                .label(label)
                .category(category)
                .diaries(toVoiceListItems(voices))
                .build();
    }

    private List<VoiceListItem> toVoiceListItems(List<Voice> voices) {
        if (voices.isEmpty()) return List.of();

        List<Long> voiceIds = voices.stream().map(Voice::getId).toList();

        Map<Long, VoiceComposite> compositeMap = voiceCompositeAdaptor.queryByVoiceIds(voiceIds).stream()
                .collect(Collectors.toMap(vc -> vc.getVoice().getId(), vc -> vc));
        Map<Long, VoiceQuestion> questionMap = voiceQuestionRepository.findByVoice_IdIn(voiceIds).stream()
                .collect(Collectors.toMap(vq -> vq.getVoice().getId(), vq -> vq));
        Map<Long, VoiceContent> contentMap = voiceContentRepository.findByVoice_IdIn(voiceIds).stream()
                .collect(Collectors.toMap(vc -> vc.getVoice().getId(), vc -> vc));

        return voices.stream()
                .map(v -> VoiceListItem.builder()
                        .voiceId(v.getId())
                        .createdAt(v.getCreatedDate().toLocalDate())
                        .analysisStatus(v.getAnalysisStatus())
                        .emotion(compositeMap.containsKey(v.getId())
                                ? compositeMap.get(v.getId()).getTopEmotion()
                                : null)
                        .questionTitle(resolveQuestionTitle(questionMap.get(v.getId())))
                        .content(contentMap.containsKey(v.getId())
                                ? contentMap.get(v.getId()).getContent()
                                : null)
                        .build())
                .collect(Collectors.toList());
    }

    private String resolveQuestionTitle(VoiceQuestion vq) {
        if (vq == null) return null;
        List<String> questions = UserServiceQuestionStaticValues.QUESTION_MAP.get(vq.getQuestionCategory().name());
        if (questions == null || vq.getQuestionIndex() < 0 || vq.getQuestionIndex() >= questions.size()) return null;
        return questions.get(vq.getQuestionIndex());
    }
}
