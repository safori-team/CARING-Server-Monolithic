package com.caring.api.voice.controller;

import com.caring.api.common.dto.ApiResponseDto;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/api/users/analyze")
public class AnalyzeController {

    @PostMapping(value = "/chat", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponseDto<?> analyzeChat(@RequestParam("session_id") String sessionId,
                                         @RequestParam("user_id") String userId,
                                         @RequestParam String question,
                                         @RequestPart("file") MultipartFile file) {
        return ApiResponseDto.onSuccess(null);
    }
}
