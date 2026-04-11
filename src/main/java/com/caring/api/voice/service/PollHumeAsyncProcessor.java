package com.caring.api.voice.service;

import com.caring.infra.ai.hume.client.HumeBatchClient;
import com.caring.infra.ai.hume.dto.callback.HumeCallbackPayload;
import com.caring.infra.ai.hume.dto.callback.HumeInferencePrediction;
import com.caring.infra.ai.hume.dto.callback.HumeModels;
import com.caring.infra.ai.hume.dto.processed.EmotionAnalysis;
import com.caring.infra.ai.hume.dto.processed.EmotionCategoryResult;
import com.caring.infra.ai.hume.mapper.HumeResultMapper;
import com.caring.infra.ai.hume.scheduler.DiaryBatchItem;
import com.caring.infra.ai.lambda.dto.DiaryPayload;
import com.caring.infra.ai.sqs.DiarySqsProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Hume polling의 @Async 실행을 담당하는 별도 빈.
 * <p>
 * Spring @Async는 프록시를 통한 호출에서만 동작한다.
 * PollHumeAnalyzeUseCase에서 this.pollAndProcess()를 직접 호출하면 프록시를 우회하므로,
 * 비동기 로직을 이 별도 빈에 위임한다.
 */
@Slf4j
@Component
public class PollHumeAsyncProcessor {

    private static final int POLL_INTERVAL_MS = 5_000;
    private static final int MAX_WAIT_MS = 120_000;

    private final HumeBatchClient humeBatchClient;
    private final HumeResultMapper humeResultMapper;
    private final Optional<DiarySqsProducer> diarySqsProducer;

    public PollHumeAsyncProcessor(
            HumeBatchClient humeBatchClient,
            HumeResultMapper humeResultMapper,
            Optional<DiarySqsProducer> diarySqsProducer
    ) {
        this.humeBatchClient = humeBatchClient;
        this.humeResultMapper = humeResultMapper;
        this.diarySqsProducer = diarySqsProducer;
    }

    /**
     * 별도 스레드에서 job 완료까지 polling 후 SQS 전송.
     * Spring 프록시를 통해 호출되므로 @Async가 정상 동작한다.
     */
    @Async
    public void pollAndProcess(String jobId, DiaryBatchItem item) {
        try {
            String status = pollUntilComplete(jobId);
            if (!"COMPLETED".equals(status)) {
                log.error("[Poll] Hume 분석 실패 또는 타임아웃: jobId={}, status={}", jobId, status);
                return;
            }

            List<HumeCallbackPayload> predictions = humeBatchClient.getJobPredictions(jobId);
            processAndSend(predictions, item);
        } catch (Exception e) {
            log.error("[Poll] 처리 중 예외 발생: jobId={}, userId={}, error={}",
                    jobId, item.userId(), e.getMessage(), e);
        }
    }

    private String pollUntilComplete(String jobId) {
        int elapsed = 0;
        while (elapsed < MAX_WAIT_MS) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "INTERRUPTED";
            }
            elapsed += POLL_INTERVAL_MS;

            String status = humeBatchClient.getJobStatus(jobId);
            log.info("[Poll] jobId={}, status={}, elapsed={}s", jobId, status, elapsed / 1000);

            if ("COMPLETED".equals(status) || "FAILED".equals(status)) {
                return status;
            }
        }
        return "TIMEOUT";
    }

    private void processAndSend(List<HumeCallbackPayload> predictions, DiaryBatchItem item) {
        HumeModels models = predictions.stream()
                .map(HumeCallbackPayload::getResults)
                .filter(r -> r != null && r.getPredictions() != null)
                .flatMap(r -> r.getPredictions().stream())
                .map(HumeInferencePrediction::getModels)
                .findFirst()
                .orElse(null);

        if (models == null) {
            log.warn("[Poll] predictions에서 모델 데이터 없음: userId={}", item.userId());
            sendToSqs(item, null, null, "");
            return;
        }

        EmotionAnalysis emotionAnalysis = humeResultMapper.toEmotionAnalysis(models);
        EmotionCategoryResult emotionCategory = humeResultMapper.computeEmotionCategory(emotionAnalysis);
        String sttText = humeResultMapper.extractSttText(models);

        log.info("[Poll] 감정 분석 완료: userId={}, topEmotion={}", item.userId(), emotionCategory.getTopEmotion());
        sendToSqs(item, emotionAnalysis, emotionCategory, sttText);
    }

    private void sendToSqs(DiaryBatchItem item, EmotionAnalysis emotionAnalysis,
                           EmotionCategoryResult emotionCategory, String sttText) {
        String content = (sttText != null && !sttText.isBlank()) ? sttText : "";

        DiaryPayload diaryPayload = DiaryPayload.ofMindDiary(
                item.userId(),
                item.userName(),
                item.question(),
                content,
                item.s3Url(),
                item.recordedAt(),
                emotionAnalysis,
                emotionCategory
        );

        if (diarySqsProducer.isEmpty()) {
            log.warn("[Poll] SQS 미설정 — 전송 건너뜀: userId={}", item.userId());
            return;
        }
        diarySqsProducer.get().send(diaryPayload);
        log.info("[Poll] SQS 전송 완료: userId={}, hasEmotion={}", item.userId(), emotionAnalysis != null);
    }
}
