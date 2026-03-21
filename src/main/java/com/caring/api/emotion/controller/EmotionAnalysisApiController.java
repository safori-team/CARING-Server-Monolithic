package com.caring.api.emotion.controller;

import com.caring.common.annotation.UserCode;
import com.caring.api.common.dto.ApiResponseDto;
import com.caring.api.emotion.dto.FrequencyAnalysisCombinedResponse;
import com.caring.api.emotion.dto.WeeklyAnalysisCombinedResponse;
import com.caring.api.voice.service.GetEmotionFrequencyUseCase;
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

    private final GetEmotionFrequencyUseCase getEmotionFrequencyUseCase;
    private final GetWeeklyEmotionAnalysisUseCase getWeeklyEmotionAnalysisUseCase;

    @GetMapping("/frequency")
    public ApiResponseDto<FrequencyAnalysisCombinedResponse> getCareEmotionFrequency(@UserCode String username,
                                                                                     @RequestParam String month) {
        return ApiResponseDto.onSuccess(getEmotionFrequencyUseCase.execute(username, month));
    }

    @GetMapping("/weekly")
    public ApiResponseDto<WeeklyAnalysisCombinedResponse> getCareEmotionWeekly(@UserCode String username,
                                                                               @RequestParam String month,
                                                                               @RequestParam int week) {
        return ApiResponseDto.onSuccess(getWeeklyEmotionAnalysisUseCase.execute(username, month, week));
    }
}
