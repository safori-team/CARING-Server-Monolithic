package com.caring.domain.chatbot.service;

import com.caring.common.annotation.DomainService;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.DoranEmotion;
import com.caring.domain.chatbot.entity.MessageOrigin;
import com.caring.domain.chatbot.exception.ChatbotHandler;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.entity.Voice;
import com.caring.infra.ai.gemini.prompts.DoranResponse;
import com.caring.infra.ai.gemini.prompts.ReframingPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@DomainService
@RequiredArgsConstructor
public class ChatbotDomainServiceImpl implements ChatbotDomainService {

    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;
    private final ChatbotMessageMapper mapper;

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

    @Override
    @Transactional(readOnly = true)
    public List<ReframingPrompt.HistoryTurn> loadRecentHistory(String sessionId, int limit) {
        List<ChatMessage> recent = chatMessageAdaptor.queryRecentBySessionId(sessionId, limit);
        Collections.reverse(recent);  // 시간순으로
        List<ReframingPrompt.HistoryTurn> history = new ArrayList<>(recent.size());
        for (ChatMessage m : recent) {
            history.add(new ReframingPrompt.HistoryTurn(
                    m.getUserInput(), mapper.botMessagePreview(m.getBotResponse())));
        }
        return history;
    }

    @Override
    @Transactional(readOnly = true)
    public long countTurns(String sessionId) {
        return chatMessageAdaptor.countBySessionId(sessionId);
    }

    @Override
    @Transactional
    public Long appendMessage(String sessionId, String userInput, DoranResponse botResponse,
                              MessageOrigin origin, Voice voice) {
        ChatSession session = chatSessionAdaptor.queryById(sessionId);
        ChatMessage saved = chatMessageAdaptor.save(ChatMessage.builder()
                .session(session)
                .userInput(userInput)
                .botResponse(mapper.toBotResponseJson(botResponse))
                .voice(voice)
                .origin(origin)
                .build());
        session.touch();
        chatSessionAdaptor.save(session);
        return saved.getId();
    }

    @Override
    @Transactional
    public String appendMindDiarySession(User user, Voice voice, DoranResponse botResponse) {
        ChatSession session = chatSessionAdaptor.save(ChatSession.create(user));
        chatMessageAdaptor.save(ChatMessage.builder()
                .session(session)
                .userInput(null)
                .botResponse(mapper.toBotResponseJson(botResponse))
                .voice(voice)
                .origin(MessageOrigin.MIND_DIARY)
                .build());
        // create()에서 이미 lastMessageAt이 now로 설정됨 → 별도 touch 불필요
        return session.getId();
    }
}
