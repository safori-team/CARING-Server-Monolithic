package com.caring.domain.question.exception;

import com.caring.common.exception.BaseErrorCode;
import com.caring.common.exception.ErrorStatus;
import com.caring.common.exception.GeneralException;

public class QuestionHandler extends GeneralException {

    public static final GeneralException NOT_FOUND =
            new QuestionHandler(ErrorStatus.QUESTION_NOT_FOUND);
    public QuestionHandler(BaseErrorCode code) {
        super(code);
    }
}
