package com.caring.infra.ai.hume.scheduler;

import lombok.Builder;

@Builder
public record DiaryBatchItem(
        String userId,
        String userName,
        String question,
        String s3Url,
        String recordedAt
) {}
