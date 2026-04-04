package com.caring.infra.ai.hume.scheduler;

import com.caring.infra.ai.hume.client.HumeBatchClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Component
public class HumeBatchScheduler {

    private static final int MAX_URLS_PER_JOB = 100;

    private final ConcurrentLinkedQueue<DiaryBatchItem> queue = new ConcurrentLinkedQueue<>();

    /**
     * S3 URL → DiaryBatchItem 매핑.
     * Hume callback 수신 시 source URL로 메타데이터를 조회하기 위함.
     */
    private final Map<String, DiaryBatchItem> pendingItems = new ConcurrentHashMap<>();

    private final HumeBatchClient humeBatchClient;
    private final String humeCallbackUrl;

    public HumeBatchScheduler(
            HumeBatchClient humeBatchClient,
            @Qualifier("humeCallbackUrl") String humeCallbackUrl
    ) {
        this.humeBatchClient = humeBatchClient;
        this.humeCallbackUrl = humeCallbackUrl;
    }

    /**
     * 마음일기 분석 요청을 큐에 적재한다.
     */
    public void enqueue(DiaryBatchItem item) {
        queue.offer(item);
        log.debug("마음일기 분석 요청 큐 적재: userId={}, s3Url={}", item.userId(), item.s3Url());
    }

    /**
     * S3 URL로 대기 중인 DiaryBatchItem을 원자적으로 클레임한다.
     * ConcurrentHashMap.remove()로 단일 호출자만 item을 획득한다.
     * 동시에 같은 callback이 두 번 들어와도 한 쪽만 non-null을 받는다.
     *
     * <p>SQS 전송 실패 시 {@link #restorePendingItem(String, DiaryBatchItem)}으로
     * 되돌려야 다음 Hume retry에서 재처리된다.
     */
    public DiaryBatchItem claimPendingItem(String s3Url) {
        return pendingItems.remove(s3Url);
    }

    /**
     * SQS 전송 실패 시 클레임한 item을 복원해 다음 callback retry에서 재처리되도록 한다.
     */
    public void restorePendingItem(String s3Url, DiaryBatchItem item) {
        pendingItems.putIfAbsent(s3Url, item);
    }

    /**
     * 테스트/즉시 분석용: 큐를 거치지 않고 단건을 즉시 Hume에 전송한다.
     *
     * @return Hume jobId
     */
    public String triggerNow(DiaryBatchItem item) {
        String url = item.s3Url();
        pendingItems.put(url, item);
        try {
            String jobId = humeBatchClient.startJob(List.of(url), humeCallbackUrl);
            log.info("Hume 즉시 분석 요청: jobId={}, userId={}", jobId, item.userId());
            return jobId;
        } catch (Exception e) {
            pendingItems.remove(url);
            throw e;
        }
    }

    /**
     * 1분마다 큐에 쌓인 요청을 모아 Hume Batch Job을 생성한다.
     * 100건 초과 시 여러 Job으로 분할.
     */
    @Scheduled(fixedRate = 60_000)
    public void flush() {
        if (queue.isEmpty()) return;

        List<DiaryBatchItem> batch = new ArrayList<>();
        DiaryBatchItem item;
        while ((item = queue.poll()) != null) {
            batch.add(item);
        }

        log.info("마음일기 배칭 flush: {}건", batch.size());

        // 100건씩 분할하여 Batch Job 생성
        for (int i = 0; i < batch.size(); i += MAX_URLS_PER_JOB) {
            List<DiaryBatchItem> chunk = batch.subList(i, Math.min(i + MAX_URLS_PER_JOB, batch.size()));
            List<String> urls = chunk.stream().map(DiaryBatchItem::s3Url).toList();

            // 메타데이터 Map에 등록 (callback에서 참조)
            chunk.forEach(bi -> pendingItems.put(bi.s3Url(), bi));

            try {
                String jobId = humeBatchClient.startJob(urls, humeCallbackUrl);
                log.info("Hume Batch Job 생성: jobId={}, chunk={}/{}", jobId, urls.size(), batch.size());
            } catch (Exception e) {
                log.error("Hume Batch Job 생성 실패 — 다음 flush에 재시도: chunkSize={}, error={}",
                        chunk.size(), e.getMessage(), e);
                // pendingItems 정리 후 큐에 재적재 — 다음 flush 사이클에서 재시도
                chunk.forEach(bi -> {
                    pendingItems.remove(bi.s3Url());
                    queue.offer(bi);
                });
            }
        }
    }
}
