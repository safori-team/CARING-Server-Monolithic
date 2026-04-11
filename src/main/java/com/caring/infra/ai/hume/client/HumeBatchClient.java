package com.caring.infra.ai.hume.client;

import com.caring.infra.ai.hume.dto.callback.HumeCallbackPayload;
import com.caring.infra.ai.hume.dto.request.HumeBatchJobRequest;
import com.caring.infra.ai.hume.dto.response.HumeBatchJobResponse;
import com.caring.infra.ai.hume.dto.response.HumeJobStatusResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HumeBatchClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration PREDICTIONS_TIMEOUT = Duration.ofSeconds(30);

    private final WebClient humeWebClient;
    private final ObjectMapper objectMapper;

    /**
     * Hume Batch API에 분석 Job을 생성한다.
     *
     * @param urls        분석할 오디오 파일 URL 목록 (최대 100건)
     * @param callbackUrl 분석 완료 시 결과를 받을 callback URL
     * @return job_id
     */
    public String startJob(List<String> urls, String callbackUrl) {
        HumeBatchJobRequest request = HumeBatchJobRequest.of(urls, callbackUrl);

        HumeBatchJobResponse response = humeWebClient.post()
                .uri("/batch/jobs")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(HumeBatchJobResponse.class)
                .block(DEFAULT_TIMEOUT);

        if (response == null || response.getJobId() == null) {
            throw new RuntimeException("Hume Batch Job 생성 실패: 응답이 null");
        }

        log.info("Hume Batch Job 생성 완료: jobId={}, urls={}", response.getJobId(), urls.size());
        return response.getJobId();
    }

    /**
     * Hume Job 상태를 조회한다.
     *
     * @return status: QUEUED | IN_PROGRESS | COMPLETED | FAILED
     */
    public String getJobStatus(String jobId) {
        HumeJobStatusResponse response = humeWebClient.get()
                .uri("/batch/jobs/{jobId}", jobId)
                .retrieve()
                .bodyToMono(HumeJobStatusResponse.class)
                .block(DEFAULT_TIMEOUT);

        return response != null ? response.getStatus() : null;
    }

    /**
     * Hume Job 분석 결과(predictions)를 조회한다.
     * callback payload와 동일한 구조.
     */
    public List<HumeCallbackPayload> getJobPredictions(String jobId) {
        String raw = humeWebClient.get()
                .uri("/batch/jobs/{jobId}/predictions", jobId)
                .retrieve()
                .bodyToMono(String.class)
                .block(PREDICTIONS_TIMEOUT);

        if (raw == null || raw.isBlank()) return List.of();

        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Hume predictions 파싱 실패: jobId={}, error={}", jobId, e.getMessage());
            return List.of();
        }
    }
}
