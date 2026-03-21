package com.caring.api.fcm.controller;

import com.caring.api.common.dto.ApiResponseDto;
import com.caring.api.fcm.service.FcmService;
import com.caring.api.fcm.dto.request.FcmSingleMessageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/api/fcm")
@RequiredArgsConstructor
public class FcmApiController {

    private final FcmService fcmService;

    @PostMapping("/send")
    public ApiResponseDto<?> sendMessage(@RequestBody FcmSingleMessageRequest request) {
        return ApiResponseDto.onSuccess(null);
    }
}
