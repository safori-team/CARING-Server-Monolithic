package com.caring.api.voice.controller;

import com.caring.api.common.dto.ApiResponseDto;
import com.caring.api.voice.service.PollHumeAnalyzeUseCase;
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
    private final PollHumeAnalyzeUseCase pollHumeAnalyzeUseCase;

    /**
     * 특정 voiceId를 즉시 Hume AI 분석 요청한다. (callback 방식, 테스트/디버그용)
     * POST /v1/api/admin/voices/{voiceId}/hume-analyze
     *
     * @return Hume jobId
     */
    @PostMapping("/{voiceId}/hume-analyze")
    public ApiResponseDto<String> triggerHumeAnalyze(@PathVariable Long voiceId) {
        return ApiResponseDto.onSuccess(triggerHumeAnalyzeUseCase.execute(voiceId));
    }

    /**
     * 특정 voiceId를 Hume AI 분석 후 SQS까지 완결 처리한다. (polling 방식, 테스트/디버그용)
     * callback URL 불필요 — 로컬 환경에서도 동작.
     * 최대 120초 대기 후 타임아웃.
     * POST /v1/api/admin/voices/{voiceId}/hume-analyze/poll
     *
     * @return Hume jobId
     */
    @PostMapping("/{voiceId}/hume-analyze/poll")
    public ApiResponseDto<String> pollHumeAnalyze(@PathVariable Long voiceId) {
        return ApiResponseDto.onSuccess(pollHumeAnalyzeUseCase.execute(voiceId));
    }
}
