package com.caring.infra.ai.hume.callback;

import com.caring.infra.ai.hume.dto.callback.HumeCallbackPayload;
import com.caring.infra.ai.hume.dto.callback.HumeInferencePrediction;
import com.caring.infra.ai.hume.dto.callback.HumeModels;
import com.caring.infra.ai.hume.dto.processed.EmotionAnalysis;
import com.caring.infra.ai.hume.dto.processed.EmotionCategoryResult;
import com.caring.infra.ai.hume.mapper.HumeResultMapper;
import com.caring.infra.ai.hume.scheduler.DiaryBatchItem;
import com.caring.infra.ai.hume.scheduler.HumeBatchScheduler;
import com.caring.infra.ai.lambda.dto.DiaryPayload;
import com.caring.infra.ai.sqs.DiarySqsProducer;
import com.caring.infra.ai.sqs.SqsSendException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
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
    private final ObjectMapper objectMapper;
    private final Optional<DiarySqsProducer> diarySqsProducer;

    public HumeCallbackController(
            HumeBatchScheduler humeBatchScheduler,
            HumeResultMapper humeResultMapper,
            ObjectMapper objectMapper,
            Optional<DiarySqsProducer> diarySqsProducer
    ) {
        this.humeBatchScheduler = humeBatchScheduler;
        this.humeResultMapper = humeResultMapper;
        this.objectMapper = objectMapper;
        this.diarySqsProducer = diarySqsProducer;
    }

    /**
     * Hume Batch API 분석 완료 callback을 수신한다.
     * 각 source URL에 대해 가공 후 SQS로 전달.
     *
     * <p>응답 코드 정책:
     * <ul>
     *   <li>400 — 요청 페이로드 파싱 실패. 동일 페이로드 재시도는 무의미하므로 Hume에 ack.</li>
     *   <li>500 — SQS 전송 실패. pendingItem이 남아 있으므로 Hume이 재시도 시 복구 가능.</li>
     *   <li>200 — 정상 처리 또는 재시도해도 결과가 같은 오류(매핑 실패 등).</li>
     * </ul>
     */
    @PostMapping("/callback")
    public ResponseEntity<Void> handleCallback(@RequestBody String rawBody) {
        log.info("Hume callback raw (first 500): {}", rawBody.substring(0, Math.min(500, rawBody.length())));
        List<HumeCallbackPayload> payloads;
        try {
            payloads = objectMapper.readValue(rawBody, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Hume callback 파싱 실패: bodyLength={}, error={}", rawBody.length(), e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        log.info("Hume callback 수신: {}건", payloads.size());

        boolean sqsFailed = false;
        for (HumeCallbackPayload payload : payloads) {
            try {
                processPayload(payload);
            } catch (SqsSendException e) {
                // SQS 실패 — pendingItem이 ack되지 않았으므로 500 반환해 Hume 재시도 유도
                log.error("Hume callback SQS 전송 실패: source={}, error={}",
                        payload.getSource() != null ? payload.getSource().getUrl() : "unknown",
                        e.getMessage(), e);
                sqsFailed = true;
            } catch (Exception e) {
                // 감정 매핑 등 내부 오류 — 재시도해도 결과가 같으므로 200으로 ack
                log.error("Hume callback 처리 실패: source={}, error={}",
                        payload.getSource() != null ? payload.getSource().getUrl() : "unknown",
                        e.getMessage(), e);
            }
        }

        if (sqsFailed) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok().build();
    }

    private void processPayload(HumeCallbackPayload payload) {
        String sourceUrl = payload.getSource() != null ? payload.getSource().getUrl() : null;

        // 원자적 클레임 — 동시 callback이 와도 한 쪽만 non-null을 획득
        DiaryBatchItem item = sourceUrl != null
                ? humeBatchScheduler.claimPendingItem(sourceUrl)
                : null;

        if (item == null) {
            log.warn("Hume callback: 매칭되는 DiaryBatchItem 없음. sourceUrl={}", sourceUrl);
            return;
        }

        try {
            // 에러 체크 — Hume 분석 실패 시 emotion_analysis = null
            if (payload.getError() != null) {
                log.warn("Hume 분석 실패: sourceUrl={}, error={}", sourceUrl, payload.getError());
                sendToSqs(item, null, null, "");
                return;
            }

            // 가공
            HumeModels models = extractModels(payload);
            if (models == null) {
                log.warn("Hume 결과에 모델 데이터 없음: sourceUrl={}", sourceUrl);
                sendToSqs(item, null, null, "");
                return;
            }

            EmotionAnalysis emotionAnalysis = humeResultMapper.toEmotionAnalysis(models);
            EmotionCategoryResult emotionCategory = humeResultMapper.computeEmotionCategory(emotionAnalysis);
            String sttText = humeResultMapper.extractSttText(models);

            log.info("감정 카테고리 산출: sourceUrl={}, topEmotion={}, confidence={}bps",
                    sourceUrl, emotionCategory.getTopEmotion(), emotionCategory.getTopEmotionConfidenceBps());

            sendToSqs(item, emotionAnalysis, emotionCategory, sttText);

        } catch (SqsSendException e) {
            // SQS 실패 — 클레임한 item을 복원해 다음 Hume retry에서 재처리 가능하게
            humeBatchScheduler.restorePendingItem(sourceUrl, item);
            throw e;  // handleCallback으로 전파 → 500 반환
        }
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
            log.warn("SQS 미설정 — 마음일기 메시지 전송 건너뜀: userId={}", item.userId());
            return;
        }
        // SqsSendException 발생 시 processPayload()의 catch에서 item을 복원
        diarySqsProducer.get().send(diaryPayload);
        log.info("마음일기 SQS 전송 완료: userId={}, hasEmotion={}",
                item.userId(), emotionAnalysis != null);
    }
}
