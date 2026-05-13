package com.caring.api.chatbot.service;

import com.caring.api.chatbot.dto.ReframingResponse;
import com.caring.api.chatbot.dto.VoiceReframingRequest;
import com.caring.common.annotation.UseCase;
import com.caring.domain.chatbot.adaptor.ChatSessionAdaptor;
import com.caring.domain.chatbot.entity.ChatSession;
import com.caring.domain.chatbot.entity.MessageOrigin;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.chatbot.service.ChatbotMessageMapper;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * 음성 채팅. 트랜잭션 분리는 텍스트 채팅과 동일.
 * voiceId가 있으면 분석 결과(VoiceComposite + VoiceEmotionLabel)를 프롬프트에 주입한다.
 */
@Slf4j
@UseCase
@RequiredArgsConstructor
public class SendVoiceReframingMessageUseCase {

    private static final int HISTORY_LIMIT = 5;

    private final UserAdaptor userAdaptor;
    private final ChatSessionAdaptor chatSessionAdaptor;
    private final ChatbotDomainService chatbotDomainService;
    private final ChatbotMessageMapper mapper;
    private final GeminiChatbotClient geminiChatbotClient;
    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;

    public ReframingResponse execute(String username, VoiceReframingRequest request) {
        // 1) 권한 검증 + 컨텍스트 로딩
        User user = userAdaptor.queryUserByUsername(username);
        ChatSession session = chatSessionAdaptor.queryById(request.sessionId());
        chatbotDomainService.verifyOwnership(session, user);

        long turnCount = chatbotDomainService.countTurns(session.getId()) + 1;
        List<ReframingPrompt.HistoryTurn> history =
                chatbotDomainService.loadRecentHistory(session.getId(), HISTORY_LIMIT);

        // 음성 분석 컨텍스트 (실패 시 텍스트 모드로 폴백)
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
                emotionDesc = mapper.summarizeVoiceEmotion(composite.orElse(null), labels);
                emotionHint = mapper.emotionHint(composite.orElse(null));
            } catch (Exception e) {
                log.warn("voiceId={} lookup failed, falling back to text mode", request.voiceId(), e);
                voice = null;
            }
        }

        String prompt = VoiceReframingPrompt.build(
                request.userInput(), history, (int) turnCount,
                user.getName() == null ? "내담자" : user.getName(),
                emotionDesc, emotionHint);

        // 2) LLM 호출
        DoranResponse llm = geminiChatbotClient.generate(prompt);

        // 3) 메시지 INSERT + 세션 touch
        Long messageId = chatbotDomainService.appendMessage(
                session.getId(), request.userInput(), llm, MessageOrigin.USER_VOICE, voice);

        return new ReframingResponse(
                messageId,
                llm.empathy(), llm.detectedDistortion(), llm.analysis(),
                llm.socraticQuestion(), llm.alternativeThought(), llm.topEmotion()
        );
    }
}
