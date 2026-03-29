package com.caring.api.voice.service;

import com.caring.api.emotion.service.GetWeeklyEmotionReportUseCase;
import com.caring.common.annotation.UseCase;
import com.caring.common.util.DateRangeUtil;
import com.caring.api.common.dto.WeekDay;
import com.caring.domain.emotion.entity.EmotionType;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.api.emotion.dto.WeekDayEmotion;
import com.caring.api.emotion.dto.WeeklyAnalysisCombinedResponse;
import lombok.RequiredArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@UseCase
@RequiredArgsConstructor
public class GetWeeklyEmotionAnalysisUseCase {

    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final GetWeeklyEmotionReportUseCase getWeeklyEmotionReportUseCase;

    /**
     * @param month "yyyy-MM" 형식
     * @param week  1-indexed 주차 (1 = 첫째 주)
     */
    public WeeklyAnalysisCombinedResponse execute(String username, String month, int week) {
        DateRangeUtil.DateRange range = DateRangeUtil.calendarWeekRange(month, week);
        LocalDate weekStart = range.getStart().toLocalDate();
        LocalDate rangeStart = range.getStart().toLocalDate();
        LocalDate rangeEndExclusive = range.getEnd().toLocalDate();
        // 실제 순회 시작일: "주 시작일"과 "클램프된 월 범위 시작일" 중 더 늦은 날짜
        LocalDate startDate = weekStart.isAfter(rangeStart) ? weekStart : rangeStart;
        // 실제 순회 종료일(배타): "주 시작 + 7일"과 "클램프된 월 범위 종료일(배타)" 중 더 이른 날짜
        LocalDate endDateExclusive = weekStart.plusDays(7).isBefore(rangeEndExclusive)
                ? weekStart.plusDays(7)
                : rangeEndExclusive;

        List<VoiceComposite> composites = voiceCompositeAdaptor.queryByUsernameAndDateRange(
                username,
                range.getStart(),
                range.getEnd()
        );

        // 날짜별로 그룹화 후 가장 많은 top_emotion 선택
        Map<LocalDate, List<VoiceComposite>> byDate = composites.stream()
                .collect(Collectors.groupingBy(vc -> vc.getCreatedDate().toLocalDate()));

        List<WeekDayEmotion> weeklyEmotions = new ArrayList<>();
        for (LocalDate date = startDate; date.isBefore(endDateExclusive); date = date.plusDays(1)) {
            List<VoiceComposite> dailyList = byDate.getOrDefault(date, List.of());

            EmotionType topEmotion = dailyList.stream()
                    .filter(vc -> vc.getTopEmotion() != null)
                    .collect(Collectors.groupingBy(VoiceComposite::getTopEmotion, Collectors.counting()))
                    .entrySet().stream()
                    .max(Comparator.comparingLong(Map.Entry::getValue))
                    .map(Map.Entry::getKey)
                    .orElse(null);

            weeklyEmotions.add(WeekDayEmotion.builder()
                    .date(date)
                    .weekDay(toWeekDay(date.getDayOfWeek()))
                    .emotionType(topEmotion)
                    .build());
        }

        String reportMessage = getWeeklyEmotionReportUseCase.execute(
                username,
                month,
                week,
                weeklyEmotions,
                composites
        );

        return WeeklyAnalysisCombinedResponse.builder()
                .weeklyEmotions(weeklyEmotions)
                .reportMessage(reportMessage)
                .build();
    }

    private WeekDay toWeekDay(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> WeekDay.MON;
            case TUESDAY -> WeekDay.TUE;
            case WEDNESDAY -> WeekDay.WED;
            case THURSDAY -> WeekDay.THU;
            case FRIDAY -> WeekDay.FRI;
            case SATURDAY -> WeekDay.SAT;
            case SUNDAY -> WeekDay.SUN;
        };
    }
}
