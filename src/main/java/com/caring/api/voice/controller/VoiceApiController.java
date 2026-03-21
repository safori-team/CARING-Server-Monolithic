package com.caring.api.voice.controller;

import com.caring.common.annotation.UserCode;
import com.caring.api.common.dto.ApiResponseDto;
import com.caring.domain.question.entity.QuestionCategory;
import com.caring.api.voice.service.GetUserVoiceDetailUseCase;
import com.caring.api.voice.service.GetUserVoiceListUseCase;
import com.caring.api.voice.service.UploadVoiceFileUseCase;
import com.caring.api.voice.dto.VoiceDetailResponse;
import com.caring.api.voice.dto.VoiceListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/users/voices")
public class VoiceApiController {

    private final UploadVoiceFileUseCase uploadVoiceFileUsecase;
    private final GetUserVoiceDetailUseCase getUserVoiceDetailUseCase;
    private final GetUserVoiceListUseCase getUserVoiceListUseCase;

    @GetMapping
    public ApiResponseDto<VoiceListResponse> getUserVoiceList(@UserCode String username,
                                                              @RequestParam(required = false) String date) {
        VoiceListResponse result = (date == null) ?
                getUserVoiceListUseCase.execute(username) : getUserVoiceListUseCase.execute(username, date);
        return ApiResponseDto.onSuccess(result);
    }

    @GetMapping("/{voiceId}")
    public ApiResponseDto<VoiceDetailResponse> getUserVoiceDetail(@PathVariable Long voiceId,
                                                                  @UserCode String username) {
        return ApiResponseDto.onSuccess(getUserVoiceDetailUseCase.execute(voiceId, username));
    }

    @DeleteMapping("/{voiceId}")
    public ApiResponseDto<?> deleteUserVoice(@PathVariable Long voiceId,
                                             @UserCode String username) {
        return ApiResponseDto.onSuccess(null);
    }

    @PostMapping
    public ApiResponseDto<Long> uploadVoiceWithQuestion(@UserCode String username,
                                                        @RequestParam QuestionCategory questionCategory,
                                                        @RequestParam int questionIndex,
                                                        @RequestParam String bucketUrl) {
        return ApiResponseDto.onSuccess(
                uploadVoiceFileUsecase.execute(username, questionCategory, questionIndex, bucketUrl));
    }
}
