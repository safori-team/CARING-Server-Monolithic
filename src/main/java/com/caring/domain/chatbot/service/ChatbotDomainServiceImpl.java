package com.caring.domain.chatbot.service;

import com.caring.common.annotation.DomainService;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.DoranEmotion;
import com.caring.domain.chatbot.exception.ChatbotHandler;
import com.caring.domain.user.entity.User;

@DomainService
public class ChatbotDomainServiceImpl implements ChatbotDomainService {

    @Override
    public void verifyOwnership(ChatSession session, User user) {
        if (!session.getUser().getId().equals(user.getId())) {
            throw ChatbotHandler.SESSION_NO_PERMISSION;
        }
    }

    @Override
    public void verifyOwnership(ChatMessage message, User user) {
        if (!message.getSession().getUser().getId().equals(user.getId())) {
            throw ChatbotHandler.MESSAGE_NO_PERMISSION;
        }
    }

    @Override
    public DoranEmotion parseFeedbackEmotion(String emotionCode) {
        try {
            return DoranEmotion.fromCode(emotionCode);
        } catch (IllegalArgumentException e) {
            throw ChatbotHandler.FEEDBACK_INVALID_EMOTION;
        }
    }
}
