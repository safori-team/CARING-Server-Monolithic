package com.caring.domain.chatbot.exception;

import com.caring.common.exception.BaseErrorCode;
import com.caring.common.exception.ErrorStatus;
import com.caring.common.exception.GeneralException;

public class ChatbotHandler extends GeneralException {

    public static final GeneralException SESSION_NOT_FOUND =
            new ChatbotHandler(ErrorStatus.CHAT_SESSION_NOT_FOUND);
    public static final GeneralException SESSION_NO_PERMISSION =
            new ChatbotHandler(ErrorStatus.CHAT_SESSION_NO_PERMISSION);
    public static final GeneralException MESSAGE_NOT_FOUND =
            new ChatbotHandler(ErrorStatus.CHAT_MESSAGE_NOT_FOUND);
    public static final GeneralException MESSAGE_NO_PERMISSION =
            new ChatbotHandler(ErrorStatus.CHAT_MESSAGE_NO_PERMISSION);
    public static final GeneralException FEEDBACK_INVALID_EMOTION =
            new ChatbotHandler(ErrorStatus.CHAT_FEEDBACK_INVALID_EMOTION);

    public ChatbotHandler(BaseErrorCode code) {
        super(code);
    }
}
