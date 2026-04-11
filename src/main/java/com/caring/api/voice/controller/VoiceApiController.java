package com.caring.api.voice.controller;

import com.caring.common.annotation.UserCode;
import com.caring.api.common.dto.ApiResponseDto;
import com.caring.api.voice.dto.PresignedUrlResponse;
import com.caring.domain.question.entity.QuestionCategory;
import com.caring.api.voice.service.GenerateVoicePresignedUrlUseCase;
import com.caring.api.voice.service.GetUserVoiceDetailUseCase;
import com.caring.api.voice.service.GetUserVoiceListUseCase;
import com.caring.api.voice.service.CreateVoiceDummyDataUseCase;
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
    private final CreateVoiceDummyDataUseCase createVoiceDummyDataUseCase;
    private final GetUserVoiceDetailUseCase getUserVoiceDetailUseCase;
    private final GetUserVoiceListUseCase getUserVoiceListUseCase;
    private final GenerateVoicePresignedUrlUseCase generateVoicePresignedUrlUseCase;

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

    /**
     * S3 음성 파일 업로드용 Presigned PUT URL 발급
     *
     * @param extension 파일 확장자 (예: m4a, mp3)
     * @return presignedUrl (PUT 업로드용, 10분 유효), voiceKey (업로드 완료 후 POST /voices에 전달)
     */
    @GetMapping("/presigned-url")
    public ApiResponseDto<PresignedUrlResponse> getPresignedUrl(@UserCode String username,
                                                                 @RequestParam String extension) {
        return ApiResponseDto.onSuccess(generateVoicePresignedUrlUseCase.execute(username, extension));
    }

    /**
     * 음성 파일 업로드 완료 등록
     *
     * @param voiceKey S3 오브젝트 키 (presigned-url 발급 시 응답받은 voiceKey)
     */
    @PostMapping
    public ApiResponseDto<Long> uploadVoiceWithQuestion(@UserCode String username,
                                                        @RequestParam QuestionCategory questionCategory,
                                                        @RequestParam int questionIndex,
                                                        @RequestParam String voiceKey,
                                                        @RequestParam String recordedAt) {
        return ApiResponseDto.onSuccess(
                uploadVoiceFileUsecase.execute(username, questionCategory, questionIndex, voiceKey, recordedAt));
    }

    @PostMapping("/test-dummy")
    public ApiResponseDto<java.util.List<Long>> createVoiceDummyData(@UserCode String username) {
        return ApiResponseDto.onSuccess(createVoiceDummyDataUseCase.execute(username));
    }
}
