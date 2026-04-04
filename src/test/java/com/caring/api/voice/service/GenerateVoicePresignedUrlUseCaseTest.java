package com.caring.api.voice.service;

import com.caring.api.voice.dto.PresignedUrlResponse;
import com.caring.common.service.S3PresignService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GenerateVoicePresignedUrlUseCaseTest {

    @Mock
    private S3PresignService s3PresignService;

    @Test
    @DisplayName("S3 설정이 있을 때 - presignedUrl과 voiceKey 반환")
    void execute_withS3_returnsPresignedUrlResponse() {
        // given
        String username = "testUser";
        String extension = "m4a";
        String expectedVoiceKey = "voices/testUser/uuid.m4a";
        String expectedPresignedUrl = "https://bucket.s3.amazonaws.com/" + expectedVoiceKey + "?X-Amz-Signature=abc";

        given(s3PresignService.generatePutUrl(username, extension))
                .willReturn(new S3PresignService.PresignedUploadResult(expectedPresignedUrl, expectedVoiceKey));

        GenerateVoicePresignedUrlUseCase useCase = new GenerateVoicePresignedUrlUseCase(Optional.of(s3PresignService));

        // when
        PresignedUrlResponse response = useCase.execute(username, extension);

        // then
        assertThat(response.getPresignedUrl()).isEqualTo(expectedPresignedUrl);
        assertThat(response.getVoiceKey()).isEqualTo(expectedVoiceKey);
    }

    @Test
    @DisplayName("S3 미설정 시 - IllegalStateException 발생")
    void execute_withoutS3_throwsException() {
        // given
        GenerateVoicePresignedUrlUseCase useCase = new GenerateVoicePresignedUrlUseCase(Optional.empty());

        // when & then
        assertThatThrownBy(() -> useCase.execute("user", "m4a"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("S3가 구성되지 않았습니다");
    }
}
