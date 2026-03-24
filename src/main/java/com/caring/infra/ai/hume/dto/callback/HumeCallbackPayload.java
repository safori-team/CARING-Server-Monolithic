package com.caring.infra.ai.hume.dto.callback;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Hume Batch API callback으로 수신되는 최상위 페이로드.
 * GET /v0/batch/jobs/{id}/predictions 응답과 동일한 구조.
 * 배열 형태로 수신됨 — 각 항목이 하나의 입력 파일에 대응.
 */
@Getter
@NoArgsConstructor
public class HumeCallbackPayload {
    private HumeSource source;
    private HumeResults results;
    private String error;
}
