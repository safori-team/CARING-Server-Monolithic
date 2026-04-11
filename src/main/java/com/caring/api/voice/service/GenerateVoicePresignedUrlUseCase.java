package com.caring.api.voice.service;

import com.caring.api.voice.dto.PresignedUrlResponse;
import com.caring.common.annotation.UseCase;
import com.caring.common.service.S3PresignService;

import java.util.Optional;

@UseCase
public class GenerateVoicePresignedUrlUseCase {

    private final Optional<S3PresignService> s3PresignService;

    public GenerateVoicePresignedUrlUseCase(Optional<S3PresignService> s3PresignService) {
        this.s3PresignService = s3PresignService;
    }

    public PresignedUrlResponse execute(String username, String extension) {
        S3PresignService service = s3PresignService
                .orElseThrow(() -> new IllegalStateException("S3가 구성되지 않았습니다. AWS 설정을 확인해주세요."));

        S3PresignService.PresignedUploadResult result = service.generatePutUrl(username, extension);
        return PresignedUrlResponse.builder()
                .presignedUrl(result.presignedUrl())
                .voiceKey(result.voiceKey())
                .build();
    }
}
