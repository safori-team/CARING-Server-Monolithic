package com.caring.infra.ai.hume.client;

import com.caring.infra.ai.hume.dto.request.HumeBatchJobRequest;
import com.caring.infra.ai.hume.dto.response.HumeBatchJobResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HumeBatchClient {

    private final WebClient humeWebClient;

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
                .block();

        if (response == null || response.getJobId() == null) {
            throw new RuntimeException("Hume Batch Job 생성 실패: 응답이 null");
        }

        log.info("Hume Batch Job 생성 완료: jobId={}, urls={}", response.getJobId(), urls.size());
        return response.getJobId();
    }
}
