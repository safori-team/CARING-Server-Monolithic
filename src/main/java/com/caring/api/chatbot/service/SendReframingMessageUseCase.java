package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ReframingRequest;
import com.caring.api.chatbot.dto.ReframingResponse;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.MessageOrigin;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.infra.ai.gemini.GeminiChatbotClient;
import com.caring.infra.ai.gemini.prompts.DoranResponse;
import com.caring.infra.ai.gemini.prompts.ReframingPrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@UseCase
@RequiredArgsConstructor
public class SendReframingMessageUseCase {

    private static final int HISTORY_LIMIT = 5;
    private static final ObjectMapper OM = new ObjectMapper();

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;
    private final ChatbotDomainService chatbotDomainService;
    private final GeminiChatbotClient geminiChatbotClient;

    @Transactional
    public ReframingResponse execute(String username, ReframingRequest request) {
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = chatSessionAdaptor.queryById(request.sessionId());
        chatbotDomainService.verifyOwnership(session, user);

        long turnCount = chatMessageAdaptor.countBySessionId(session.getId()) + 1;
        List<ChatMessage> recent = chatMessageAdaptor.queryRecentBySessionId(
                session.getId(), HISTORY_LIMIT);
        Collections.reverse(recent);

        List<ReframingPrompt.HistoryTurn> history = new ArrayList<>(recent.size());
        for (ChatMessage m : recent) {
            history.add(new ReframingPrompt.HistoryTurn(m.getUserInput(), readBotMessage(m.getBotResponse())));
        }

        String prompt = ReframingPrompt.build(
                request.userInput(), history, (int) turnCount, request.emotion());

        DoranResponse llm = geminiChatbotClient.generate(prompt);
        JsonNode botResponseJson = OM.valueToTree(toMap(llm));

        ChatMessage saved = chatMessageAdaptor.save(ChatMessage.builder()
                .session(session)
                .userInput(request.userInput())
                .botResponse(botResponseJson)
                .origin(MessageOrigin.USER_TEXT)
                .build());

        return new ReframingResponse(
                saved.getId(),
                llm.empathy(), llm.detectedDistortion(), llm.analysis(),
                llm.socraticQuestion(), llm.alternativeThought(), llm.topEmotion()
        );
    }

    private String readBotMessage(JsonNode bot) {
        if (bot == null) return "";
        JsonNode q = bot.get("socratic_question");
        if (q != null && !q.isNull()) return q.asText();
        JsonNode e = bot.get("empathy");
        return e == null || e.isNull() ? "" : e.asText();
    }

    private Map<String, String> toMap(DoranResponse r) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("empathy", r.empathy());
        m.put("detected_distortion", r.detectedDistortion());
        m.put("analysis", r.analysis());
        m.put("socratic_question", r.socraticQuestion());
        m.put("alternative_thought", r.alternativeThought());
        m.put("emotion", r.topEmotion());
        return m;
    }
}
