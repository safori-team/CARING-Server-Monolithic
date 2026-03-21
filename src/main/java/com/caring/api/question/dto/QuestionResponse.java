package com.caring.api.question.dto;

import com.caring.domain.question.entity.QuestionCategory;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Builder
@Getter
@RequiredArgsConstructor
public class QuestionResponse {
    private final Long id;
    private final QuestionCategory questionCategory;
    private final String content;
}
