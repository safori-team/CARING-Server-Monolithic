package com.caring.infra.ai.hume.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class HumeJobStatusResponse {

    @JsonProperty("job_id")
    private String jobId;

    private State state;

    public String getStatus() {
        return state != null ? state.status : null;
    }

    @Getter
    @NoArgsConstructor
    public static class State {
        private String status;
    }
}
