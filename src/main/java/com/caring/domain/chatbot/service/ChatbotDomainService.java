package com.caring.domain.chatbot.service;

import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.DoranEmotion;
import com.caring.domain.user.entity.User;

public interface ChatbotDomainService {

    void verifyOwnership(ChatSession session, User user);

    void verifyOwnership(ChatMessage message, User user);

    DoranEmotion parseFeedbackEmotion(String emotionCode);
}
