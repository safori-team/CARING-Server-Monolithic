package com.caring.api.voice.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.consts.UserServiceQuestionStaticValues;
import com.caring.common.service.S3PresignService;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.repository.VoiceQuestionRepository;
import com.caring.infra.ai.hume.client.HumeBatchClient;
import com.caring.infra.ai.hume.scheduler.DiaryBatchItem;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * 테스트/디버그용 Hume 분석 유스케이스.
 * callback 없이 polling으로 결과를 직접 수신하여 SQS까지 전송한다.
 * polling은 PollHumeAsyncProcessor 빈을 통해 비동기 처리되므로 HTTP 커넥션을 즉시 해제한다.
 */
@Slf4j
@UseCase
public class PollHumeAnalyzeUseCase {

    private final VoiceAdaptor voiceAdaptor;
    private final VoiceQuestionRepository voiceQuestionRepository;
    private final HumeBatchClient humeBatchClient;
    private final PollHumeAsyncProcessor pollHumeAsyncProcessor;
    private final Optional<S3PresignService> s3PresignService;

    public PollHumeAnalyzeUseCase(
            VoiceAdaptor voiceAdaptor,
            VoiceQuestionRepository voiceQuestionRepository,
            HumeBatchClient humeBatchClient,
            PollHumeAsyncProcessor pollHumeAsyncProcessor,
            Optional<S3PresignService> s3PresignService
    ) {
        this.voiceAdaptor = voiceAdaptor;
        this.voiceQuestionRepository = voiceQuestionRepository;
        this.humeBatchClient = humeBatchClient;
        this.pollHumeAsyncProcessor = pollHumeAsyncProcessor;
        this.s3PresignService = s3PresignService;
    }

    /**
     * Hume job을 제출하고 jobId를 즉시 반환한다.
     * polling 및 SQS 전송은 PollHumeAsyncProcessor를 통해 백그라운드에서 비동기 처리된다.
     *
     * @return Hume jobId
     */
    public String submit(Long voiceId) {
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

        String jobId = humeBatchClient.startJob(List.of(humeAccessUrl), null);
        log.info("[Poll] Hume job 제출: jobId={}, userId={}", jobId, item.userId());

        // 별도 빈(PollHumeAsyncProcessor)을 통해 호출 — Spring 프록시를 거쳐 @Async 정상 동작
        pollHumeAsyncProcessor.pollAndProcess(jobId, item);
        return jobId;
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
                .orElseThrow(() -> new IllegalStateException(
                        "S3가 구성되지 않아 Hume 분석을 실행할 수 없습니다. AWS 설정을 확인하세요."));
    }
}
