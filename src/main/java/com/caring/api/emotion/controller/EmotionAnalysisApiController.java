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

    @GetMapping("/monthly")
    public ApiResponseDto<MonthlyAnalysisCombinedResponse> getCareEmotionMonthly(@UserCode String username,
                                                                                 @RequestParam String yearMonth) {
        return ApiResponseDto.onSuccess(getMonthlyEmotionAnalysisUseCase.execute(username, yearMonth));
    }

    @GetMapping("/weekly")
    public ApiResponseDto<WeeklyAnalysisCombinedResponse> getCareEmotionWeekly(@UserCode String username,
                                                                               @RequestParam String yearMonth,
                                                                               @RequestParam int week) {
        return ApiResponseDto.onSuccess(getWeeklyEmotionAnalysisUseCase.execute(username, yearMonth, week));
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
