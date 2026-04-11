package com.caring.api.voice.service;

import com.caring.common.service.S3PresignService;
import com.caring.domain.question.entity.QuestionCategory;
import com.caring.domain.question.entity.VoiceQuestion;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.repository.VoiceQuestionRepository;
import com.caring.infra.ai.hume.scheduler.DiaryBatchItem;
import com.caring.infra.ai.hume.scheduler.HumeBatchScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TriggerHumeAnalyzeUseCaseTest {

    @Mock VoiceAdaptor voiceAdaptor;
    @Mock VoiceQuestionRepository voiceQuestionRepository;
    @Mock HumeBatchScheduler humeBatchScheduler;
    @Mock S3PresignService s3PresignService;
    @Mock Voice voice;
    @Mock User user;
    @Mock VoiceQuestion voiceQuestion;

    @Test
    @DisplayName("즉시 분석 요청 - Hume jobId 반환 및 DiaryBatchItem 정확히 구성")
    void execute_triggersHumeAndReturnsJobId() {
        // given
        Long voiceId = 1L;
        String voiceKey = "voices/user1/uuid.m4a";
        String presignedGetUrl = "https://bucket.s3.amazonaws.com/" + voiceKey + "?X-Amz-Signature=abc";
        String expectedJobId = "hume-job-123";

        given(voiceAdaptor.queryById(voiceId)).willReturn(voice);
        given(voice.getVoiceKey()).willReturn(voiceKey);
        given(voice.getCreatedDate()).willReturn(LocalDateTime.of(2026, 4, 4, 0, 0));
        given(voice.getUser()).willReturn(user);
        given(user.getUserUuid()).willReturn("user-uuid-abc");
        given(user.getName()).willReturn("홍길동");

        given(voiceQuestion.getQuestionCategory()).willReturn(QuestionCategory.EMOTION);
        given(voiceQuestion.getQuestionIndex()).willReturn(0);
        given(voiceQuestionRepository.findByVoice_Id(voiceId)).willReturn(Optional.of(voiceQuestion));

        given(s3PresignService.generateGetUrl(voiceKey)).willReturn(presignedGetUrl);
        given(humeBatchScheduler.triggerNow(any(DiaryBatchItem.class))).willReturn(expectedJobId);

        TriggerHumeAnalyzeUseCase useCase = new TriggerHumeAnalyzeUseCase(
                voiceAdaptor, voiceQuestionRepository, humeBatchScheduler, Optional.of(s3PresignService));

        // when
        String jobId = useCase.execute(voiceId);

        // then
        assertThat(jobId).isEqualTo(expectedJobId);

        ArgumentCaptor<DiaryBatchItem> captor = ArgumentCaptor.forClass(DiaryBatchItem.class);
        verify(humeBatchScheduler).triggerNow(captor.capture());

        DiaryBatchItem item = captor.getValue();
        assertThat(item.s3Url()).isEqualTo(presignedGetUrl);
        assertThat(item.userId()).isEqualTo("user-uuid-abc");
        assertThat(item.userName()).isEqualTo("홍길동");
        assertThat(item.question()).isEqualTo("오늘 기분을 한 단어로 표현하면 무엇인가요?");
    }

    @Test
    @DisplayName("VoiceQuestion 없을 때 - question 빈 문자열로 처리")
    void execute_noVoiceQuestion_usesEmptyQuestion() {
        // given
        Long voiceId = 1L;
        given(voiceAdaptor.queryById(voiceId)).willReturn(voice);
        given(voice.getVoiceKey()).willReturn("voices/user1/uuid.m4a");
        given(voice.getCreatedDate()).willReturn(LocalDateTime.now());
        given(voice.getUser()).willReturn(user);
        given(user.getUserUuid()).willReturn("uuid");
        given(user.getName()).willReturn("홍길동");
        given(voiceQuestionRepository.findByVoice_Id(voiceId)).willReturn(Optional.empty());
        given(s3PresignService.generateGetUrl(any())).willReturn("https://url");
        given(humeBatchScheduler.triggerNow(any())).willReturn("job-id");

        TriggerHumeAnalyzeUseCase useCase = new TriggerHumeAnalyzeUseCase(
                voiceAdaptor, voiceQuestionRepository, humeBatchScheduler, Optional.of(s3PresignService));

        // when
        useCase.execute(voiceId);

        // then
        ArgumentCaptor<DiaryBatchItem> captor = ArgumentCaptor.forClass(DiaryBatchItem.class);
        verify(humeBatchScheduler).triggerNow(captor.capture());
        assertThat(captor.getValue().question()).isEmpty();
    }

    @Test
    @DisplayName("S3 미설정 시 - IllegalStateException 발생")
    void execute_withoutS3_throwsIllegalStateException() {
        // given
        Long voiceId = 1L;
        String voiceKey = "voices/user1/uuid.m4a";

        given(voiceAdaptor.queryById(voiceId)).willReturn(voice);
        given(voice.getVoiceKey()).willReturn(voiceKey);
        given(voice.getCreatedDate()).willReturn(LocalDateTime.now());
        given(voice.getUser()).willReturn(user);
        given(user.getUserUuid()).willReturn("uuid");
        given(user.getName()).willReturn("홍길동");
        given(voiceQuestionRepository.findByVoice_Id(voiceId)).willReturn(Optional.empty());

        TriggerHumeAnalyzeUseCase useCase = new TriggerHumeAnalyzeUseCase(
                voiceAdaptor, voiceQuestionRepository, humeBatchScheduler, Optional.empty());

        // when & then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.execute(voiceId))
                .isInstanceOf(IllegalStateException.class);
    }
}
