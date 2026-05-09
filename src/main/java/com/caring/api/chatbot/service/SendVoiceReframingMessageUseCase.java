package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ReframingResponse;
import com.caring.api.chatbot.dto.VoiceReframingRequest;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.MessageOrigin;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.domain.voice.entity.VoiceEmotionLabel;
import com.caring.infra.ai.gemini.GeminiChatbotClient;
import com.caring.infra.ai.gemini.prompts.DoranResponse;
import com.caring.infra.ai.gemini.prompts.ReframingPrompt;
import com.caring.infra.ai.gemini.prompts.VoiceReframingPrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@UseCase
@RequiredArgsConstructor
public class SendVoiceReframingMessageUseCase {

    private static final int HISTORY_LIMIT = 5;
    private static final ObjectMapper OM = new ObjectMapper();

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;
    private final ChatbotDomainService chatbotDomainService;
    private final GeminiChatbotClient geminiChatbotClient;
    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;

    @Transactional
    public ReframingResponse execute(String username, VoiceReframingRequest request) {
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = chatSessionAdaptor.queryById(request.sessionId());
        chatbotDomainService.verifyOwnership(session, user);

        long turnCount = chatMessageAdaptor.countBySessionId(session.getId()) + 1;
        List<ChatMessage> recent = chatMessageAdaptor.queryRecentBySessionId(
                session.getId(), HISTORY_LIMIT);
        Collections.reverse(recent);

        List<ReframingPrompt.HistoryTurn> history = new ArrayList<>(recent.size());
        for (ChatMessage m : recent) {
            history.add(new ReframingPrompt.HistoryTurn(
                    m.getUserInput(), readBotMessage(m.getBotResponse())));
        }

        Voice voice = null;
        String emotionDesc = null;
        String emotionHint = null;
        if (request.voiceId() != null) {
            try {
                voice = voiceAdaptor.queryById(request.voiceId());
                Optional<VoiceComposite> composite = voiceCompositeAdaptor
                        .queryByVoiceIds(List.of(voice.getId())).stream().findFirst();
                List<VoiceEmotionLabel> labels = voiceEmotionLabelAdaptor
                        .findByVoiceId(voice.getId());
                emotionDesc = formatEmotionDesc(composite.orElse(null), labels);
                emotionHint = composite
                        .map(c -> c.getTopEmotion().name().toLowerCase(Locale.ROOT))
                        .orElse(null);
            } catch (Exception e) {
                log.warn("voiceId={} lookup failed, falling back to text mode", request.voiceId(), e);
                voice = null;
            }
        }

        String prompt = VoiceReframingPrompt.build(
                request.userInput(), history, (int) turnCount,
                user.getName() == null ? "내담자" : user.getName(),
                emotionDesc, emotionHint);

        DoranResponse llm = geminiChatbotClient.generate(prompt);
        JsonNode botResponseJson = OM.valueToTree(toMap(llm));

        ChatMessage saved = chatMessageAdaptor.save(ChatMessage.builder()
                .session(session)
                .userInput(request.userInput())
                .botResponse(botResponseJson)
                .voice(voice)
                .origin(MessageOrigin.USER_VOICE)
                .build());

        return new ReframingResponse(
                saved.getId(),
                llm.empathy(), llm.detectedDistortion(), llm.analysis(),
                llm.socraticQuestion(), llm.alternativeThought(), llm.topEmotion()
        );
    }

    private String formatEmotionDesc(VoiceComposite composite, List<VoiceEmotionLabel> labels) {
        if (composite == null) return "(음성 분석 미완료)";
        StringBuilder sb = new StringBuilder();
        sb.append("- 주된 감정: **").append(composite.getTopEmotion().name().toLowerCase(Locale.ROOT)).append("**");
        sb.append(" (강도 bps: ").append(composite.getTopEmotionConfidenceBps()).append(")\n");
        if (labels != null && !labels.isEmpty()) {
            List<VoiceEmotionLabel> top3 = labels.stream()
                    .sorted((a, b) -> Integer.compare(b.getIntensityX1000(), a.getIntensityX1000()))
                    .limit(3)
                    .toList();
            sb.append("- 세부 감정 top: ");
            for (int i = 0; i < top3.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(top3.get(i).getLabel())
                        .append("(").append(top3.get(i).getIntensityX1000() / 1000.0).append(")");
            }
        }
        return sb.toString();
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
