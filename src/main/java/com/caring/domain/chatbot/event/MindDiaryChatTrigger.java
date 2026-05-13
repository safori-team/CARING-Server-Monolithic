package com.caring.domain.chatbot.event;

import com.caring.common.consts.UserServiceQuestionStaticValues;
import com.caring.common.event.VoiceAnalysisCompletedEvent;
import com.caring.domain.chatbot.service.ChatbotDomainService;
import com.caring.domain.chatbot.service.ChatbotMessageMapper;
import com.caring.domain.question.entity.VoiceQuestion;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.adaptor.VoiceAdaptor;
import com.caring.domain.voice.adaptor.VoiceCompositeAdaptor;
import com.caring.domain.voice.adaptor.VoiceEmotionLabelAdaptor;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.entity.VoiceComposite;
import com.caring.domain.voice.entity.VoiceContent;
import com.caring.domain.voice.entity.VoiceEmotionLabel;
import com.caring.domain.voice.repository.VoiceContentRepository;
import com.caring.domain.voice.repository.VoiceQuestionRepository;
import com.caring.infra.ai.gemini.GeminiChatbotClient;
import com.caring.infra.ai.gemini.prompts.DoranResponse;
import com.caring.infra.ai.gemini.prompts.MindDiaryPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

/**
 * 마음일기(음성 일기) 분석 완료 시 도란이 첫 메시지를 생성한다.
 * <p>
 * 트랜잭션 경계는 ChatbotDomainService.appendMindDiarySession에 위임 (트랜잭션 분리).
 * 본 리스너 자체는 비-트랜잭션 — Gemini 호출이 DB 커넥션을 점유하지 않는다.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MindDiaryChatTrigger {

    private final VoiceAdaptor voiceAdaptor;
    private final VoiceCompositeAdaptor voiceCompositeAdaptor;
    private final VoiceEmotionLabelAdaptor voiceEmotionLabelAdaptor;
    private final VoiceQuestionRepository voiceQuestionRepository;
    private final VoiceContentRepository voiceContentRepository;
    private final ChatbotDomainService chatbotDomainService;
    private final ChatbotMessageMapper mapper;
    private final GeminiChatbotClient geminiChatbotClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVoiceAnalysisCompleted(VoiceAnalysisCompletedEvent event) {
        try {
            // 1) Voice + 분석 컨텍스트 + 일기 정보 로딩
            Voice voice = voiceAdaptor.queryById(event.voiceId());
            User user = voice.getUser();

            Optional<VoiceComposite> composite = voiceCompositeAdaptor
                    .queryByVoiceIds(List.of(voice.getId())).stream().findFirst();
            List<VoiceEmotionLabel> labels = voiceEmotionLabelAdaptor
                    .findByVoiceId(voice.getId());
            String questionText = resolveQuestionText(voice.getId());
            String content = voiceContentRepository.findByVoice_Id(voice.getId())
                    .map(VoiceContent::getContent)
                    .orElse(null);

            String emotionDesc = mapper.summarizeVoiceEmotion(composite.orElse(null), labels);
            String emotionHint = mapper.emotionHint(composite.orElse(null));

            String prompt = MindDiaryPrompt.build(
                    user.getName() == null ? "내담자" : user.getName(),
                    questionText,
                    content,
                    voice.getCreatedDate() == null ? "알 수 없음" : voice.getCreatedDate().toString(),
                    emotionDesc, emotionHint);

            // 2) LLM 호출 (트랜잭션 밖)
            DoranResponse llm = geminiChatbotClient.generate(prompt);

            // 3) 세션 + 첫 봇 메시지 저장 (별도 짧은 트랜잭션)
            String sessionId = chatbotDomainService.appendMindDiarySession(user, voice, llm);

            log.info("MindDiaryChatTrigger: created session={} for voiceId={}, user={}",
                    sessionId, voice.getId(), user.getUsername());

        } catch (Exception e) {
            log.error("MindDiaryChatTrigger failed for voiceId={} (silent fail)",
                    event.voiceId(), e);
        }
    }

    /**
     * voiceId의 VoiceQuestion → QUESTION_MAP에서 질문 텍스트 조회.
     * 일기에 매핑된 질문이 없거나 인덱스가 잘못됐으면 "(자유 일기)".
     */
    private String resolveQuestionText(Long voiceId) {
        Optional<VoiceQuestion> vq = voiceQuestionRepository.findByVoice_Id(voiceId);
        if (vq.isEmpty()) return "(자유 일기)";
        VoiceQuestion q = vq.get();
        List<String> questions = UserServiceQuestionStaticValues.QUESTION_MAP
                .get(q.getQuestionCategory().name());
        if (questions == null
                || q.getQuestionIndex() < 0
                || q.getQuestionIndex() >= questions.size()) {
            return "(자유 일기)";
        }
        return questions.get(q.getQuestionIndex());
    }
}
