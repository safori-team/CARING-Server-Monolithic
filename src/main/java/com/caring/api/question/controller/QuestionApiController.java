package com.caring.api.question.controller;

import com.caring.api.common.dto.ApiResponseDto;
import com.caring.api.question.service.GetAllQuestionsUseCase;
import com.caring.api.question.service.GetRandomQuestionUseCase;
import com.caring.api.question.dto.QuestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/api/users/questions")
@RequiredArgsConstructor
public class QuestionApiController {

    private final GetAllQuestionsUseCase getAllQuestionsUseCase;
    private final GetRandomQuestionUseCase getRandomQuestionUseCase;

    @GetMapping
    public ApiResponseDto<List<QuestionResponse>> getAllQuestions() {
        return ApiResponseDto.onSuccess(getAllQuestionsUseCase.execute());
    }

    @GetMapping("/random")
    public ApiResponseDto<QuestionResponse> getRandomQuestion() {
        return ApiResponseDto.onSuccess(getRandomQuestionUseCase.execute());
    }
}
