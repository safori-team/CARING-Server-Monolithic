package com.caring.common.exception;

public interface BaseErrorCode extends BaseCode{
    String getExplainError() throws NoSuchFieldException;
}
