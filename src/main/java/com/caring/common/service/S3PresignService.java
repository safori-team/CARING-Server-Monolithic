package com.caring.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@ConditionalOnBean(S3Presigner.class)
@RequiredArgsConstructor
public class S3PresignService {

    private final S3Presigner s3Presigner;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    private static final Duration PUT_EXPIRY = Duration.ofMinutes(10);
    private static final Duration GET_EXPIRY = Duration.ofHours(1);

    /**
     * 음성 파일 업로드용 Presigned PUT URL을 발급한다.
     * 경로: voices/{username}/{UUID}.{extension}
     *
     * @param username  인증된 사용자명 (S3 경로 scoping용)
     * @param extension 파일 확장자 (예: m4a, mp3)
     * @return presignedUrl + voiceKey
     */
    public PresignedUploadResult generatePutUrl(String username, String extension) {
        String voiceKey = "voices/" + username + "/" + UUID.randomUUID() + "." + extension;

        String presignedUrl = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(PUT_EXPIRY)
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(voiceKey)
                                .build())
                        .build()
        ).url().toString();

        return new PresignedUploadResult(presignedUrl, voiceKey);
    }

    /**
     * 음성 파일 조회용 Presigned GET URL을 발급한다. (유효기간 1시간)
     *
     * @param voiceKey S3 오브젝트 키 (예: voices/user1/uuid.m4a)
     * @return presigned GET URL
     */
    public String generateGetUrl(String voiceKey) {
        return s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(GET_EXPIRY)
                        .getObjectRequest(GetObjectRequest.builder()
                                .bucket(bucket)
                                .key(voiceKey)
                                .build())
                        .build()
        ).url().toString();
    }

    public record PresignedUploadResult(String presignedUrl, String voiceKey) {}
}
