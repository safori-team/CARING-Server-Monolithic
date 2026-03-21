package com.caring.api.question.service;

import com.caring.common.annotation.UseCase;
import com.caring.common.consts.UserServiceQuestionStaticValues;
import com.caring.domain.question.entity.QuestionCategory;
import com.caring.api.question.dto.QuestionResponse;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@UseCase
@RequiredArgsConstructor
public class GetAllQuestionsUseCase {

    public List<QuestionResponse> execute() {
        List<QuestionResponse> responses = new ArrayList<>();
        long sequence = 1L;

        for (QuestionCategory category : QuestionCategory.values()) {
            List<String> questions = UserServiceQuestionStaticValues.QUESTION_MAP
                    .getOrDefault(category.name(), List.of());

            for (String content : questions) {
                responses.add(QuestionResponse.builder()
                        .id(sequence++)
                        .questionCategory(category)
                        .content(content)
                        .build());
            }
        }

        return responses;
    }
}
