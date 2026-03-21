package com.caring.api.fcm.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FcmSingleMessageRequest {
    private String token;
    private String title;
    private String body;
//    private Map<String, String> data;
}
