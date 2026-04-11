package com.caring.infra.ai.hume.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HumeBatchJobResponse {
    @JsonProperty("job_id")
    private String jobId;
}
