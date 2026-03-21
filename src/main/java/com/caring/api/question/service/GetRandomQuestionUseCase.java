package com.caring.api.question.service;

import com.caring.common.annotation.UseCase;
import com.caring.api.question.dto.QuestionResponse;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@UseCase
@RequiredArgsConstructor
public class GetRandomQuestionUseCase {

    private final GetAllQuestionsUseCase getAllQuestionsUseCase;

    public QuestionResponse execute() {
        List<QuestionResponse> questions = getAllQuestionsUseCase.execute();
        if (questions.isEmpty()) {
            return null;
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(questions.size());
        return questions.get(randomIndex);
    }
}
