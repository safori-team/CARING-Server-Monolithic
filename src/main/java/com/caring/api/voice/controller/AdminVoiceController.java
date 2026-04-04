package com.caring.api.voice.controller;

import com.caring.api.common.dto.ApiResponseDto;
import com.caring.api.voice.service.TriggerHumeAnalyzeUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/admin/voices")
public class AdminVoiceController {

    private final TriggerHumeAnalyzeUseCase triggerHumeAnalyzeUseCase;

    /**
     * 특정 voiceId를 즉시 Hume AI 분석 요청한다. (테스트/디버그용)
     * POST /v1/api/admin/voices/{voiceId}/hume-analyze
     *
     * @return Hume jobId
     */
    @PostMapping("/{voiceId}/hume-analyze")
    public ApiResponseDto<String> triggerHumeAnalyze(@PathVariable Long voiceId) {
        return ApiResponseDto.onSuccess(triggerHumeAnalyzeUseCase.execute(voiceId));
    }
}
