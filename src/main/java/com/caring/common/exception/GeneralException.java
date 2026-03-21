package com.caring.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GeneralException extends RuntimeException{
    private BaseErrorCode code;

    @Override
    public String getMessage() {
        return code.getReason().getMessage();
    }

    public Reason getErrorReason() {
        return this.code.getReason();
    }

    public Reason getErrorReasonHttpStatus() {
        return this.code.getReasonHttpStatus();
    }

}
