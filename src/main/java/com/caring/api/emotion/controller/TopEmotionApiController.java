package com.caring.api.emotion.controller;

import com.caring.common.annotation.UserCode;
import com.caring.api.common.dto.ApiResponseDto;
import com.caring.domain.emotion.entity.EmotionType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/api/users/top_emotion")
public class TopEmotionApiController {

    @GetMapping
    public ApiResponseDto<EmotionType> getUserTopEmotion(@UserCode String username) {
        return ApiResponseDto.onSuccess(null);
    }

}
