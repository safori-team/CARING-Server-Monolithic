package com.caring.infra.ai.sqs;

public class SqsSendException extends RuntimeException {

    public SqsSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
