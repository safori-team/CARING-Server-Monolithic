package com.caring.api.emotion.service;

import com.caring.common.annotation.UseCase;
import com.caring.api.emotion.dto.WeekDayEmotion;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.domain.voice.entity.WeeklyEmotionReport;
import com.caring.domain.voice.repository.WeeklyEmotionReportRepository;
import com.caring.infra.openai.OpenAiWeeklyReportClient;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@UseCase
@Transactional
@RequiredArgsConstructor
public class GetWeeklyEmotionReportUseCase {

    private static final String NO_DATA_MESSAGE = "해당 주에는 감정분석 데이터가 없었습니다.";

    private final UserAdaptor userAdaptor;
    private final WeeklyEmotionReportRepository weeklyEmotionReportRepository;
    private final OpenAiWeeklyReportClient openAiWeeklyReportClient;

    public String execute(String username, String month, int week, List<WeekDayEmotion> weeklyEmotions, List<VoiceComposite> composites) {
        User user = userAdaptor.queryUserByUsername(username);
        // 가장 최근에 업데이트 된 주간 데이터 있는지 확인
        Long latestVoiceCompositeId = composites.stream()
                .max(Comparator
                        .comparing(VoiceComposite::getCreatedDate)
                        .thenComparing(VoiceComposite::getId))
                .map(VoiceComposite::getId)
                .orElse(null);

        // 기존에 리포트 작성한 데이터 불러오기(업데이트 시간 비교용)
        WeeklyEmotionReport cached = weeklyEmotionReportRepository
                .findByUser_IdAndReportMonthAndReportWeek(user.getId(), month, week)
                .orElse(null);

        //TODO more readable
        if (latestVoiceCompositeId == null) {
            if (cached != null && cached.getLatestVoiceCompositeId() == null) {
                return cached.getReportMessage();
            }
            return saveOrUpdate(cached, user, month, week, null, NO_DATA_MESSAGE).getReportMessage();
        }

        if (cached != null && Objects.equals(cached.getLatestVoiceCompositeId(), latestVoiceCompositeId)) {
            return cached.getReportMessage();
        }

        String reportMessage = openAiWeeklyReportClient.generateWeeklyReport(user.getName(), weeklyEmotions);
        return saveOrUpdate(cached, user, month, week, latestVoiceCompositeId, reportMessage).getReportMessage();
    }

    private WeeklyEmotionReport saveOrUpdate(WeeklyEmotionReport cached,
                                             User user,
                                             String month,
                                             int week,
                                             Long latestVoiceCompositeId,
                                             String reportMessage) {
        if (cached != null) {
            cached.update(latestVoiceCompositeId, reportMessage);
            return cached;
        }

        WeeklyEmotionReport created = WeeklyEmotionReport.builder()
                .user(user)
                .reportMonth(month)
                .reportWeek(week)
                .latestVoiceCompositeId(latestVoiceCompositeId)
                .reportMessage(reportMessage)
                .build();
        return weeklyEmotionReportRepository.save(created);
    }
}
