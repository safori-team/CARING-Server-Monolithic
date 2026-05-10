package com.caring.domain.chatbot.event;

import com.caring.common.event.VoiceAnalysisCompletedEvent;
import com.caring.domain.chatbot.adaptor.ChatMessageAdaptor;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatMessage;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.MessageOrigin;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.domain.voice.entity.VoiceEmotionLabel;
import com.caring.infra.ai.gemini.GeminiChatbotClient;
import com.caring.infra.ai.gemini.prompts.DoranResponse;
import com.caring.infra.ai.gemini.prompts.MindDiaryPrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MindDiaryChatTrigger {

    private static final ObjectMapper OM = new ObjectMapper();

    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatMessageAdaptor chatMessageAdaptor;
    private final GeminiChatbotClient geminiChatbotClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoiceAnalysisCompleted(VoiceAnalysisCompletedEvent event) {
        try {
            Voice voice = voiceAdaptor.queryById(event.voiceId());
            User user = voice.getUser();

            Optional<VoiceComposite> composite = voiceCompositeAdaptor
                    .queryByVoiceIds(List.of(voice.getId())).stream().findFirst();
            List<VoiceEmotionLabel> labels = voiceEmotionLabelAdaptor
                    .findByVoiceId(voice.getId());

            String emotionDesc = formatEmotionDesc(composite.orElse(null), labels);
            String emotionHint = composite
                    .map(c -> c.getTopEmotion().name().toLowerCase(Locale.ROOT))
                    .orElse(null);

            String prompt = MindDiaryPrompt.build(
                    user.getName() == null ? "내담자" : user.getName(),
                    "(자유 일기)",
                    null,
                    voice.getCreatedDate() == null ? "알 수 없음" : voice.getCreatedDate().toString(),
                    emotionDesc, emotionHint);

            DoranResponse llm = geminiChatbotClient.generate(prompt);
            JsonNode botResponseJson = OM.valueToTree(toMap(llm));

            ChatSession session = chatSessionAdaptor.save(ChatSession.create(user));
            chatMessageAdaptor.save(ChatMessage.builder()
                    .session(session)
                    .userInput(null)
                    .botResponse(botResponseJson)
                    .voice(voice)
                    .origin(MessageOrigin.MIND_DIARY)
                    .build());

            log.info("MindDiaryChatTrigger: created session={} for voiceId={}, user={}",
                    session.getId(), voice.getId(), user.getUsername());

        } catch (Exception e) {
            log.error("MindDiaryChatTrigger failed for voiceId={} (silent fail)",
                    event.voiceId(), e);
        }
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
