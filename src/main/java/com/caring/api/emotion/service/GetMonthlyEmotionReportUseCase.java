package com.caring.api.emotion.service;

import com.caring.common.annotation.UseCase;
import com.caring.domain.emotion.entity.EmotionType;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.entity.MonthlyEmotionReport;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.domain.voice.repository.MonthlyEmotionReportRepository;
import com.caring.infra.openai.OpenAiMonthlyReportClient;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@UseCase
@Transactional
@RequiredArgsConstructor
public class GetMonthlyEmotionReportUseCase {

    private static final String NO_DATA_MESSAGE = "해당 달에는 감정 분석 데이터가 없었습니다.";

    private final UserAdaptor userAdaptor;
    private final MonthlyEmotionReportRepository monthlyEmotionReportRepository;
    private final OpenAiMonthlyReportClient openAiMonthlyReportClient;

    public String execute(String username, String month, Map<EmotionType, Long> emotionCounts, List<VoiceComposite> composites) {
        User user = userAdaptor.queryUserByUsername(username);
        Long latestVoiceCompositeId = composites.stream()
                .max(Comparator
                        .comparing(VoiceComposite::getCreatedDate)
                        .thenComparing(VoiceComposite::getId))
                .map(VoiceComposite::getId)
                .orElse(null);

        MonthlyEmotionReport cached = monthlyEmotionReportRepository
                .findByUser_IdAndReportMonth(user.getId(), month)
                .orElse(null);

        if (latestVoiceCompositeId == null) {
            if (cached != null && cached.getLatestVoiceCompositeId() == null) {
                return cached.getReportMessage();
            }
            return saveOrUpdate(cached, user, month, null, NO_DATA_MESSAGE).getReportMessage();
        }

        if (cached != null && Objects.equals(cached.getLatestVoiceCompositeId(), latestVoiceCompositeId)) {
            return cached.getReportMessage();
        }

        String reportMessage = openAiMonthlyReportClient.generateMonthlyReport(user.getName(), emotionCounts);
        return saveOrUpdate(cached, user, month, latestVoiceCompositeId, reportMessage).getReportMessage();
    }

    private MonthlyEmotionReport saveOrUpdate(MonthlyEmotionReport cached,
                                              User user,
                                              String month,
                                              Long latestVoiceCompositeId,
                                              String reportMessage) {
        if (cached != null) {
            cached.update(latestVoiceCompositeId, reportMessage);
            return cached;
        }

        MonthlyEmotionReport created = MonthlyEmotionReport.builder()
                .user(user)
                .reportMonth(month)
                .latestVoiceCompositeId(latestVoiceCompositeId)
                .reportMessage(reportMessage)
                .build();
        return monthlyEmotionReportRepository.save(created);
    }
}
