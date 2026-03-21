package com.caring.api.fcm.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FcmResponse {
    private boolean success;
    private String message;
    private String messageId;
    private int successCount;
    private int failureCount;
}
