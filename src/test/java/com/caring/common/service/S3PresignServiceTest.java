package com.caring.common.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.MalformedURLException;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class S3PresignServiceTest {

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private PresignedPutObjectRequest presignedPutObjectRequest;

    @Mock
    private PresignedGetObjectRequest presignedGetObjectRequest;

    private S3PresignService s3PresignService;

    @BeforeEach
    void setUp() {
        s3PresignService = new S3PresignService(s3Presigner);
        ReflectionTestUtils.setField(s3PresignService, "bucket", "caring-voice-recordings");
    }

    @Test
    @DisplayName("PUT Presigned URL 발급 - voiceKey 경로 형식 검증")
    void generatePutUrl_voiceKeyFormat() throws MalformedURLException {
        // given
        given(presignedPutObjectRequest.url()).willReturn(new URL("https://caring-voice-recordings.s3.amazonaws.com/voices/user1/test.m4a?X-Amz-Signature=abc"));
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(presignedPutObjectRequest);

        // when
        S3PresignService.PresignedUploadResult result = s3PresignService.generatePutUrl("user1", "m4a");

        // then
        assertThat(result.voiceKey()).startsWith("voices/user1/");
        assertThat(result.voiceKey()).endsWith(".m4a");
        assertThat(result.presignedUrl()).contains("s3.amazonaws.com");
    }

    @Test
    @DisplayName("PUT Presigned URL 발급 - 동일 유저 요청 시 매번 다른 UUID 생성")
    void generatePutUrl_uniqueKeyPerRequest() throws MalformedURLException {
        // given
        given(presignedPutObjectRequest.url()).willReturn(new URL("https://bucket.s3.amazonaws.com/key"));
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).willReturn(presignedPutObjectRequest);

        // when
        S3PresignService.PresignedUploadResult result1 = s3PresignService.generatePutUrl("user1", "m4a");
        S3PresignService.PresignedUploadResult result2 = s3PresignService.generatePutUrl("user1", "m4a");

        // then
        assertThat(result1.voiceKey()).isNotEqualTo(result2.voiceKey());
    }

    @Test
    @DisplayName("GET Presigned URL 발급 - URL 반환 확인")
    void generateGetUrl_returnsUrl() throws MalformedURLException {
        // given
        given(presignedGetObjectRequest.url()).willReturn(new URL("https://caring-voice-recordings.s3.amazonaws.com/voices/user1/uuid.m4a?X-Amz-Signature=xyz"));
        given(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class))).willReturn(presignedGetObjectRequest);

        // when
        String url = s3PresignService.generateGetUrl("voices/user1/uuid.m4a");

        // then
        assertThat(url).isNotBlank();
        assertThat(url).contains("X-Amz-Signature");
    }
}
