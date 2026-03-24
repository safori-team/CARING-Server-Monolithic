package com.caring.infra.ai.hume.callback;

import com.caring.infra.ai.hume.dto.callback.HumeCallbackPayload;
import com.caring.infra.ai.hume.dto.callback.HumeInferencePrediction;
import com.caring.infra.ai.hume.dto.callback.HumeModels;
import com.caring.infra.ai.hume.dto.processed.EmotionAnalysis;
import com.caring.infra.ai.hume.mapper.HumeResultMapper;
import com.caring.infra.ai.hume.scheduler.DiaryBatchItem;
import com.caring.infra.ai.hume.scheduler.HumeBatchScheduler;
import com.caring.infra.ai.lambda.dto.DiaryPayload;
import com.caring.infra.ai.sqs.DiarySqsProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/v1/api/hume")
public class HumeCallbackController {

    private final HumeBatchScheduler humeBatchScheduler;
    private final HumeResultMapper humeResultMapper;
    private final Optional<DiarySqsProducer> diarySqsProducer;

    public HumeCallbackController(
            HumeBatchScheduler humeBatchScheduler,
            HumeResultMapper humeResultMapper,
            Optional<DiarySqsProducer> diarySqsProducer
    ) {
        this.humeBatchScheduler = humeBatchScheduler;
        this.humeResultMapper = humeResultMapper;
        this.diarySqsProducer = diarySqsProducer;
    }

    /**
     * Hume Batch API 분석 완료 callback을 수신한다.
     * 각 source URL에 대해 가공 후 SQS로 전달.
     */
    @PostMapping("/callback")
    public ResponseEntity<Void> handleCallback(@RequestBody List<HumeCallbackPayload> payloads) {
        log.info("Hume callback 수신: {}건", payloads.size());

        for (HumeCallbackPayload payload : payloads) {
            try {
                processPayload(payload);
            } catch (Exception e) {
                log.error("Hume callback 처리 실패: source={}, error={}",
                        payload.getSource() != null ? payload.getSource().getUrl() : "unknown",
                        e.getMessage(), e);
            }
        }

        return ResponseEntity.ok().build();
    }

    private void processPayload(HumeCallbackPayload payload) {
        String sourceUrl = payload.getSource() != null ? payload.getSource().getUrl() : null;

        // 메타데이터 조회
        DiaryBatchItem item = sourceUrl != null
                ? humeBatchScheduler.consumePendingItem(sourceUrl)
                : null;

        if (item == null) {
            log.warn("Hume callback: 매칭되는 DiaryBatchItem 없음. sourceUrl={}", sourceUrl);
            return;
        }

        // 에러 체크 — Hume 분석 실패 시 emotion_analysis = null
        if (payload.getError() != null) {
            log.warn("Hume 분석 실패: sourceUrl={}, error={}", sourceUrl, payload.getError());
            sendToSqs(item, null, "");
            return;
        }

        // 가공
        HumeModels models = extractModels(payload);
        if (models == null) {
            log.warn("Hume 결과에 모델 데이터 없음: sourceUrl={}", sourceUrl);
            sendToSqs(item, null, "");
            return;
        }

        EmotionAnalysis emotionAnalysis = humeResultMapper.toEmotionAnalysis(models);
        String sttText = humeResultMapper.extractSttText(models);

        sendToSqs(item, emotionAnalysis, sttText);
    }

    private HumeModels extractModels(HumeCallbackPayload payload) {
        if (payload.getResults() == null || payload.getResults().getPredictions() == null) {
            return null;
        }
        return payload.getResults().getPredictions().stream()
                .map(HumeInferencePrediction::getModels)
                .findFirst()
                .orElse(null);
    }

    private void sendToSqs(DiaryBatchItem item, EmotionAnalysis emotionAnalysis, String sttText) {
        String content = (sttText != null && !sttText.isBlank()) ? sttText : "";

        DiaryPayload diaryPayload = DiaryPayload.ofMindDiary(
                item.userId(),
                item.userName(),
                item.question(),
                content,
                item.s3Url(),
                item.recordedAt(),
                emotionAnalysis
        );

        if (diarySqsProducer.isEmpty()) {
            log.warn("SQS 미설정 — 마음일기 메시지 전송 건너뜀: userId={}", item.userId());
            return;
        }
        diarySqsProducer.get().send(diaryPayload);
        log.info("마음일기 SQS 전송 완료: userId={}, hasEmotion={}",
                item.userId(), emotionAnalysis != null);
    }
}
