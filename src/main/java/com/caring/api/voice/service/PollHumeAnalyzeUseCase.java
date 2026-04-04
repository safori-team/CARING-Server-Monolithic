package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.consts.UserServiceQuestionStaticValues;
import com.caring.common.service.S3PresignService;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.repository.VoiceQuestionRepository;
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

import java.util.List;
import java.util.Optional;

/**
 * 테스트/디버그용 Hume 분석 유스케이스.
 * callback 없이 polling으로 결과를 직접 수신하여 SQS까지 전송한다.
 */
@Slf4j
@UseCase
public class PollHumeAnalyzeUseCase {

    private static final int POLL_INTERVAL_MS = 5_000;
    private static final int MAX_WAIT_MS = 120_000;

    private final VoiceAdaptor voiceAdaptor;
    private final VoiceQuestionRepository voiceQuestionRepository;
    private final HumeBatchClient humeBatchClient;
    private final HumeResultMapper humeResultMapper;
    private final Optional<S3PresignService> s3PresignService;
    private final Optional<DiarySqsProducer> diarySqsProducer;

    public PollHumeAnalyzeUseCase(
            VoiceAdaptor voiceAdaptor,
            VoiceQuestionRepository voiceQuestionRepository,
            HumeBatchClient humeBatchClient,
            HumeResultMapper humeResultMapper,
            Optional<S3PresignService> s3PresignService,
            Optional<DiarySqsProducer> diarySqsProducer
    ) {
        this.voiceAdaptor = voiceAdaptor;
        this.voiceQuestionRepository = voiceQuestionRepository;
        this.humeBatchClient = humeBatchClient;
        this.humeResultMapper = humeResultMapper;
        this.s3PresignService = s3PresignService;
        this.diarySqsProducer = diarySqsProducer;
    }

    /**
     * @return Hume jobId
     * @throws IllegalStateException polling 타임아웃 또는 Hume 분석 실패 시
     */
    public String execute(Long voiceId) {
        Voice voice = voiceAdaptor.queryById(voiceId);

        String questionText = resolveQuestion(voiceId);
        String humeAccessUrl = resolveHumeUrl(voice.getVoiceKey());

        DiaryBatchItem item = DiaryBatchItem.builder()
                .userId(voice.getUser().getUserUuid())
                .userName(voice.getUser().getName())
                .question(questionText)
                .s3Url(humeAccessUrl)
                .recordedAt(voice.getCreatedDate().toString())
                .build();

        // callback URL 없이 job 제출
        String jobId = humeBatchClient.startJob(List.of(humeAccessUrl), null);
        log.info("[Poll] Hume job 제출: jobId={}, userId={}", jobId, item.userId());

        // polling
        String status = pollUntilComplete(jobId);
        if (!"COMPLETED".equals(status)) {
            throw new IllegalStateException("Hume 분석 실패 또는 타임아웃: jobId=" + jobId + ", status=" + status);
        }

        // predictions 조회 및 처리
        List<HumeCallbackPayload> predictions = humeBatchClient.getJobPredictions(jobId);
        processAndSend(predictions, item);

        return jobId;
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

    private String resolveQuestion(Long voiceId) {
        return voiceQuestionRepository.findByVoice_Id(voiceId)
                .map(vq -> {
                    List<String> questions = UserServiceQuestionStaticValues.QUESTION_MAP
                            .get(vq.getQuestionCategory().name());
                    if (questions != null && vq.getQuestionIndex() < questions.size()) {
                        return questions.get(vq.getQuestionIndex());
                    }
                    return "";
                })
                .orElse("");
    }

    private String resolveHumeUrl(String voiceKey) {
        return s3PresignService
                .map(svc -> svc.generateGetUrl(voiceKey))
                .orElse(voiceKey);
    }
}
