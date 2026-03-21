package com.caring.api.emotion.controller;

import com.caring.api.common.dto.ApiResponseDto;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/api/nlp")
public class NlpController {

    @PostMapping("/sentiment")
    public ApiResponseDto<?> analyzeSentiment(@RequestParam String text,
                                              @RequestParam(defaultValue = "ko") String languageCode) {
        return ApiResponseDto.onSuccess(null);
    }

    @PostMapping("/entities")
    public ApiResponseDto<?> extractEntities(@RequestParam String text,
                                             @RequestParam(defaultValue = "ko") String languageCode) {
        return ApiResponseDto.onSuccess(null);
    }

    @PostMapping("/syntax")
    public ApiResponseDto<?> analyzeSyntax(@RequestParam String text,
                                           @RequestParam(defaultValue = "ko") String languageCode) {
        return ApiResponseDto.onSuccess(null);
    }

    @PostMapping("/analyze")
    public ApiResponseDto<?> analyzeTextComprehensive(@RequestParam String text,
                                                      @RequestParam(defaultValue = "ko") String languageCode) {
        return ApiResponseDto.onSuccess(null);
    }
}
