package com.caring.api.notification.controller;

import com.caring.common.annotation.UserCode;
import com.caring.api.common.dto.ApiResponseDto;
import com.caring.api.notification.dto.NotificationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/api/users/notifications")
public class NotificationApiController {

    @GetMapping
    public ApiResponseDto<NotificationResponse> getNotifications(@UserCode String username) {
        return ApiResponseDto.onSuccess(null);
    }
}
