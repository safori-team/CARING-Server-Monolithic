package com.caring.infra.ai.hume.dto.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Hume Batch API 콜백 최상위 래퍼.
 * 실제 콜백 페이로드 구조:
 * { "job_id": "...", "status": "COMPLETED", "predictions": [...] }
 */
@Getter
@NoArgsConstructor
public class HumeCallbackEnvelope {

    @JsonProperty("job_id")
    private String jobId;

    private String status;

    private List<HumeCallbackPayload> predictions;
}
