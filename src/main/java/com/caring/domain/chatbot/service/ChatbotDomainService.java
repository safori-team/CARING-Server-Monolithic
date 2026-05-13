package com.caring.domain.chatbot.service;

import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.DoranEmotion;
import com.caring.domain.chatbot.entity.MessageOrigin;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.entity.Voice;
import com.caring.infra.ai.gemini.prompts.DoranResponse;
import com.caring.infra.ai.gemini.prompts.ReframingPrompt;

import java.util.List;

public interface ChatbotDomainService {

    void verifyOwnership(ChatSession session, User user);

    void verifyOwnership(ChatMessage message, User user);

    DoranEmotion parseFeedbackEmotion(String emotionCode);

    /**
     * 채팅 세션의 최근 N개 메시지를 가져와 시간순(오래된 → 최근) HistoryTurn으로 변환한다.
     * (readOnly 트랜잭션 경계)
     */
    List<ReframingPrompt.HistoryTurn> loadRecentHistory(String sessionId, int limit);

    /** 세션의 누적 턴 수. (readOnly 트랜잭션 경계) */
    long countTurns(String sessionId);

    /**
     * 메시지 INSERT + 세션 lastMessageAt 갱신을 한 트랜잭션으로 처리한다.
     * @return 생성된 메시지 ID
     */
    Long appendMessage(String sessionId, String userInput, DoranResponse botResponse,
                       MessageOrigin origin, Voice voice);

    /**
     * 마음일기 트리거에서 호출 — 신규 세션 + 첫 봇 메시지를 한 트랜잭션으로 저장.
     * @return 생성된 세션 ID
     */
    String appendMindDiarySession(User user, Voice voice, DoranResponse botResponse);
}
