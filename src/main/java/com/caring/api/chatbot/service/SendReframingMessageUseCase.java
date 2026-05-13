package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ReframingRequest;
import com.caring.api.chatbot.dto.ReframingResponse;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.MessageOrigin;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.infra.ai.gemini.GeminiChatbotClient;
import com.caring.infra.ai.gemini.prompts.DoranResponse;
import com.caring.infra.ai.gemini.prompts.ReframingPrompt;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * 텍스트 채팅. 트랜잭션을 두 단계로 분리한다 (LLM 호출이 DB 커넥션을 점유하지 않도록):
 * <ol>
 *   <li>ChatbotDomainService.loadRecentHistory + countTurns (readOnly 트랜잭션)</li>
 *   <li>(트랜잭션 밖) GeminiChatbotClient.generate</li>
 *   <li>ChatbotDomainService.appendMessage (Transactional)</li>
 * </ol>
 */
@UseCase
@RequiredArgsConstructor
public class SendReframingMessageUseCase {

    private static final int HISTORY_LIMIT = 5;

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatbotDomainService chatbotDomainService;
    private final GeminiChatbotClient geminiChatbotClient;

    public ReframingResponse execute(String username, ReframingRequest request) {
        // 1) 권한 검증 + 컨텍스트 로딩 (별도 readOnly 트랜잭션 경계)
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = chatSessionAdaptor.queryById(request.sessionId());
        chatbotDomainService.verifyOwnership(session, user);

        long turnCount = chatbotDomainService.countTurns(session.getId()) + 1;
        List<ReframingPrompt.HistoryTurn> history =
                chatbotDomainService.loadRecentHistory(session.getId(), HISTORY_LIMIT);

        String prompt = ReframingPrompt.build(
                request.userInput(), history, (int) turnCount, request.emotion());

        // 2) LLM 호출 (트랜잭션 밖)
        DoranResponse llm = geminiChatbotClient.generate(prompt);

        // 3) 메시지 INSERT + 세션 touch (별도 짧은 트랜잭션)
        Long messageId = chatbotDomainService.appendMessage(
                session.getId(), request.userInput(), llm, MessageOrigin.USER_TEXT, null);

        return new ReframingResponse(
                messageId,
                llm.empathy(), llm.detectedDistortion(), llm.analysis(),
                llm.socraticQuestion(), llm.alternativeThought(), llm.topEmotion()
        );
    }
}
