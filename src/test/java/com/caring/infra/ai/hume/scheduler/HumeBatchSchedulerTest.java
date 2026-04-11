package com.caring.infra.ai.hume.scheduler;

import com.caring.infra.ai.hume.client.HumeBatchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HumeBatchSchedulerTest {

    @Mock
    private HumeBatchClient humeBatchClient;

    private HumeBatchScheduler scheduler;
    private static final String CALLBACK_URL = "http://localhost:8080/v1/api/hume/callback";

    @BeforeEach
    void setUp() {
        scheduler = new HumeBatchScheduler(humeBatchClient, CALLBACK_URL);
    }

    @Test
    @DisplayName("triggerNow - Hume에 즉시 전송하고 jobId 반환")
    void triggerNow_sendsToHumeAndReturnsJobId() {
        // given
        String jobId = "hume-job-123";
        DiaryBatchItem item = DiaryBatchItem.builder()
                .userId("user-uuid")
                .userName("홍길동")
                .question("오늘 기분은?")
                .s3Url("https://bucket.s3.amazonaws.com/voices/user1/uuid.m4a?sig=abc")
                .recordedAt("2026-04-04T00:00:00")
                .build();

        given(humeBatchClient.startJob(anyList(), eq(CALLBACK_URL))).willReturn(jobId);

        // when
        String result = scheduler.triggerNow(item);

        // then
        assertThat(result).isEqualTo(jobId);
        assertThat(scheduler.claimPendingItem(item.s3Url())).isNotNull(); // 콜백 수신 전까지 pendingItems에 존재해야 함
    }

    @Test
    @DisplayName("triggerNow - pendingItems에 등록 후 콜백에서 조회 가능")
    void triggerNow_registersInPendingItems() {
        // given
        DiaryBatchItem item = DiaryBatchItem.builder()
                .userId("user-uuid")
                .userName("홍길동")
                .question("오늘 기분은?")
                .s3Url("https://bucket.s3.amazonaws.com/voices/user1/uuid.m4a?sig=abc")
                .recordedAt("2026-04-04T00:00:00")
                .build();

        given(humeBatchClient.startJob(anyList(), eq(CALLBACK_URL))).willReturn("job-id");

        // when
        scheduler.triggerNow(item);

        // then - 콜백 시 consumePendingItem으로 조회 가능
        DiaryBatchItem found = scheduler.claimPendingItem(item.s3Url());
        assertThat(found).isNotNull();
        assertThat(found.userId()).isEqualTo("user-uuid");
    }

    @Test
    @DisplayName("triggerNow 실패 시 - pendingItems에서 제거됨")
    void triggerNow_onFailure_removeFromPendingItems() {
        // given
        DiaryBatchItem item = DiaryBatchItem.builder()
                .userId("user-uuid")
                .userName("홍길동")
                .question("오늘 기분은?")
                .s3Url("https://bucket.s3.amazonaws.com/voices/user1/uuid.m4a?sig=abc")
                .recordedAt("2026-04-04T00:00:00")
                .build();

        given(humeBatchClient.startJob(anyList(), any())).willThrow(new RuntimeException("Hume API 오류"));

        // when & then
        assertThatThrownBy(() -> scheduler.triggerNow(item))
                .isInstanceOf(RuntimeException.class);

        // pendingItems에서 제거되어야 함
        assertThat(scheduler.claimPendingItem(item.s3Url())).isNull();
    }

    @Test
    @DisplayName("flush - 큐가 비어있으면 Hume 호출 안 함")
    void flush_emptyQueue_doesNotCallHume() {
        // when
        scheduler.flush();

        // then
        verify(humeBatchClient, never()).startJob(anyList(), any());
    }

    @Test
    @DisplayName("flush - 큐에 쌓인 항목들을 Hume에 일괄 전송")
    void flush_sendsQueuedItemsToHume() {
        // given
        DiaryBatchItem item1 = buildItem("user1", "https://url1");
        DiaryBatchItem item2 = buildItem("user2", "https://url2");

        given(humeBatchClient.startJob(anyList(), eq(CALLBACK_URL))).willReturn("job-id");

        scheduler.enqueue(item1);
        scheduler.enqueue(item2);

        // when
        scheduler.flush();

        // then
        verify(humeBatchClient).startJob(List.of("https://url1", "https://url2"), CALLBACK_URL);
    }

    @Test
    @DisplayName("flush 후 - 큐 비워짐 확인 (재실행 시 Hume 호출 안 함)")
    void flush_clearsQueue() {
        // given
        given(humeBatchClient.startJob(anyList(), any())).willReturn("job-id");
        scheduler.enqueue(buildItem("user1", "https://url1"));
        scheduler.flush();

        // when - 두 번째 flush
        scheduler.flush();

        // then - 두 번째는 호출 안 됨
        verify(humeBatchClient).startJob(anyList(), any()); // 딱 1번만 호출
    }

    private DiaryBatchItem buildItem(String userId, String s3Url) {
        return DiaryBatchItem.builder()
                .userId(userId)
                .userName("테스트유저")
                .question("오늘 기분은?")
                .s3Url(s3Url)
                .recordedAt("2026-04-04T00:00:00")
                .build();
    }
}
