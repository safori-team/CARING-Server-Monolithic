package com.caring.api.voice.service;

import com.caring.common.consts.UserServiceQuestionStaticValues;
import com.caring.common.service.S3PresignService;
import com.caring.domain.question.entity.QuestionCategory;
import com.caring.domain.question.entity.VoiceQuestion;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.service.VoiceDomainService;
import com.caring.infra.ai.hume.scheduler.DiaryBatchItem;
import com.caring.infra.ai.hume.scheduler.HumeBatchScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UploadVoiceFileUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock VoiceDomainService voiceDomainService;
    @Mock HumeBatchScheduler humeBatchScheduler;
    @Mock S3PresignService s3PresignService;
    @Mock User user;
    @Mock Voice voice;

    @Test
    @DisplayName("업로드 성공 - Hume에 presigned GET URL 전달")
    void execute_withS3_sendPresignedGetUrlToHume() {
        // given
        String username = "testUser";
        String voiceKey = "voices/testUser/uuid.m4a";
        String presignedGetUrl = "https://bucket.s3.amazonaws.com/" + voiceKey + "?X-Amz-Signature=xyz";
        QuestionCategory category = QuestionCategory.EMOTION;
        int index = 0;

        given(userAdaptor.queryUserByUsername(username)).willReturn(user);
        given(user.getUserUuid()).willReturn("user-uuid-123");
        given(user.getName()).willReturn("홍길동");
        given(voiceDomainService.uploadVoiceFile(user, voiceKey)).willReturn(voice);
        given(voice.getId()).willReturn(1L);
        given(s3PresignService.generateGetUrl(voiceKey)).willReturn(presignedGetUrl);

        UploadVoiceFileUseCase useCase = new UploadVoiceFileUseCase(
                userAdaptor, voiceDomainService, humeBatchScheduler, Optional.of(s3PresignService));

        // when
        Long voiceId = useCase.execute(username, category, index, voiceKey, "2026-04-04T00:00:00");

        // then
        assertThat(voiceId).isEqualTo(1L);

        ArgumentCaptor<DiaryBatchItem> captor = ArgumentCaptor.forClass(DiaryBatchItem.class);
        verify(humeBatchScheduler).enqueue(captor.capture());

        DiaryBatchItem enqueuedItem = captor.getValue();
        assertThat(enqueuedItem.s3Url()).isEqualTo(presignedGetUrl);
        assertThat(enqueuedItem.userId()).isEqualTo("user-uuid-123");
        assertThat(enqueuedItem.question()).isEqualTo(
                UserServiceQuestionStaticValues.QUESTION_MAP.get("EMOTION").get(0));
    }

    @Test
    @DisplayName("S3 미설정 시 - voiceKey를 그대로 Hume URL로 사용")
    void execute_withoutS3_usesVoiceKeyAsUrl() {
        // given
        String username = "testUser";
        String voiceKey = "voices/testUser/uuid.m4a";

        given(userAdaptor.queryUserByUsername(username)).willReturn(user);
        given(user.getUserUuid()).willReturn("user-uuid-123");
        given(user.getName()).willReturn("홍길동");
        given(voiceDomainService.uploadVoiceFile(user, voiceKey)).willReturn(voice);
        given(voice.getId()).willReturn(1L);

        UploadVoiceFileUseCase useCase = new UploadVoiceFileUseCase(
                userAdaptor, voiceDomainService, humeBatchScheduler, Optional.empty());

        // when
        useCase.execute(username, QuestionCategory.EMOTION, 0, voiceKey, "2026-04-04T00:00:00");

        // then
        ArgumentCaptor<DiaryBatchItem> captor = ArgumentCaptor.forClass(DiaryBatchItem.class);
        verify(humeBatchScheduler).enqueue(captor.capture());
        assertThat(captor.getValue().s3Url()).isEqualTo(voiceKey);
    }
}
