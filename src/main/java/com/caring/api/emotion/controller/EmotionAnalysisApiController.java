package com.caring.api.emotion.controller;

import com.caring.common.annotation.UserCode;
import com.caring.api.common.dto.ApiResponseDto;
import com.caring.api.emotion.dto.EmotionLabelDiaryListResponse;
import com.caring.api.emotion.dto.MonthlyAnalysisCombinedResponse;
import com.caring.api.emotion.dto.MonthlyEmotionBubbleResponse;
import com.caring.api.emotion.dto.WeeklyAnalysisCombinedResponse;
import com.caring.api.emotion.service.GetEmotionLabelDiaryListUseCase;
import com.caring.api.emotion.service.GetMonthlyEmotionBubbleUseCase;
import com.caring.api.voice.service.GetMonthlyEmotionAnalysisUseCase;
import com.caring.api.voice.service.GetWeeklyEmotionAnalysisUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/users/voices/analyzing")
public class EmotionAnalysisApiController {

    private final GetMonthlyEmotionAnalysisUseCase getMonthlyEmotionAnalysisUseCase;
    private final GetWeeklyEmotionAnalysisUseCase getWeeklyEmotionAnalysisUseCase;
    private final GetMonthlyEmotionBubbleUseCase getMonthlyEmotionBubbleUseCase;
    private final GetEmotionLabelDiaryListUseCase getEmotionLabelDiaryListUseCase;

    /**
     * @param yearMonth "yyyy-MM" 형식 (신규 파라미터명)
     * @param month     "yyyy-MM" 형식 (구버전 호환, 하위 호환 지원 기간 후 제거 예정)
     */
    @GetMapping("/monthly")
    public ApiResponseDto<MonthlyAnalysisCombinedResponse> getCareEmotionMonthly(
            @UserCode String username,
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) String month) {
        return ApiResponseDto.onSuccess(
                getMonthlyEmotionAnalysisUseCase.execute(username, resolveYearMonth(yearMonth, month)));
    }

    /**
     * @param yearMonth "yyyy-MM" 형식 (신규 파라미터명)
     * @param month     "yyyy-MM" 형식 (구버전 호환, 하위 호환 지원 기간 후 제거 예정)
     */
    @GetMapping("/weekly")
    public ApiResponseDto<WeeklyAnalysisCombinedResponse> getCareEmotionWeekly(
            @UserCode String username,
            @RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) String month,
            @RequestParam int week) {
        return ApiResponseDto.onSuccess(
                getWeeklyEmotionAnalysisUseCase.execute(username, resolveYearMonth(yearMonth, month), week));
    }

    /**
     * yearMonth(신규) 또는 month(구버전 호환) 중 하나를 받아 유효한 값을 반환.
     * 둘 다 null이면 필수 파라미터 누락으로 예외를 발생시킨다.
     */
    private String resolveYearMonth(String yearMonth, String month) {
        if (yearMonth != null) return yearMonth;
        if (month != null) return month;
        throw new IllegalArgumentException("필수 파라미터 'yearMonth'가 누락되었습니다.");
    }

    /**
     * 특정 월의 세부 감정 버블차트 데이터.
     * GET /v1/api/users/voices/analyzing/bubble?yearMonth=2024-01
     *
     * @param yearMonth "yyyy-MM" 형식 (예: "2024-01")
     */
    @GetMapping("/bubble")
    public ApiResponseDto<MonthlyEmotionBubbleResponse> getMonthlyEmotionBubble(
            @UserCode String username,
            @RequestParam String yearMonth) {
        return ApiResponseDto.onSuccess(getMonthlyEmotionBubbleUseCase.execute(username, yearMonth));
    }

    /**
     * 특정 월에 특정 세부 감정을 느꼈던 일기 상세 리스트.
     * GET /v1/api/users/voices/analyzing/bubble/diaries?yearMonth=2024-01&label=joy
     *
     * @param yearMonth "yyyy-MM" 형식
     * @param label     Gemini 세부 감정 레이블 (joy, anxiety, frustration 등)
     */
    @GetMapping("/bubble/diaries")
    public ApiResponseDto<EmotionLabelDiaryListResponse> getEmotionLabelDiaries(
            @UserCode String username,
            @RequestParam String yearMonth,
            @RequestParam String label) {
        return ApiResponseDto.onSuccess(getEmotionLabelDiaryListUseCase.execute(username, yearMonth, label));
    }
}
