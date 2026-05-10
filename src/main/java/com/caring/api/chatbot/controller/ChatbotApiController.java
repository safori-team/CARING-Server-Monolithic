package com.caring.api.chatbot.controller;

import com.caring.api.chatbot.dto.ChatHistoryResponse;
import com.caring.api.chatbot.dto.CreateSessionResponse;
import com.caring.api.chatbot.dto.FeedbackRequest;
import com.caring.api.chatbot.dto.FeedbackResponse;
import com.caring.api.chatbot.dto.ReframingRequest;
import com.caring.api.chatbot.dto.ReframingResponse;
import com.caring.api.chatbot.dto.SessionListResponse;
import com.caring.api.chatbot.dto.VoiceReframingRequest;
import com.caring.api.chatbot.service.CreateChatSessionUseCase;
import com.caring.api.chatbot.service.DeleteChatSessionUseCase;
import com.caring.api.chatbot.service.GetChatHistoryUseCase;
import com.caring.api.chatbot.service.GetChatSessionsUseCase;
import com.caring.api.chatbot.service.SendReframingMessageUseCase;
import com.caring.api.chatbot.service.SendVoiceReframingMessageUseCase;
import com.caring.api.chatbot.service.UpdateMessageFeedbackUseCase;
import com.caring.api.common.dto.ApiResponseDto;
import com.caring.common.annotation.UserCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "[챗봇 - 도란이]",
     description = """
             CBT 리프레이밍 챗봇 '도란이' API.

             전형적 사용 흐름:
               1) POST /sessions  → 새 채팅방을 만들고 sessionId(UUID v4) 받기
               2) POST /reframing or /voice-reframing  → sessionId로 메시지 주고받기 (도란이 응답 즉시 반환)
               3) PUT /messages/{messageId}/feedback  → 사용자가 봇 분석에 대해 '진짜 마음' 피드백
               4) GET /sessions, /history/{sessionId} → 채팅방 목록·상세 화면
               5) DELETE /sessions/{sessionId}  → 채팅방 삭제 (cascade hard delete)

             마음일기 자동 트리거:
               별도 API 호출 없이, 마음일기(음성 일기) 분석이 끝나면 서버가 자동으로
               ChatSession을 만들고 도란이 첫 메시지를 저장한다.
               클라이언트는 푸시 알림 또는 GET /sessions로 신규 세션을 확인하면 된다.
             """)
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/api/chatbot")
public class ChatbotApiController {

    private final CreateChatSessionUseCase createChatSessionUseCase;
    private final DeleteChatSessionUseCase deleteChatSessionUseCase;
    private final GetChatSessionsUseCase getChatSessionsUseCase;
    private final GetChatHistoryUseCase getChatHistoryUseCase;
    private final SendReframingMessageUseCase sendReframingMessageUseCase;
    private final SendVoiceReframingMessageUseCase sendVoiceReframingMessageUseCase;
    private final UpdateMessageFeedbackUseCase updateMessageFeedbackUseCase;

    @PostMapping("/sessions")
    @Operation(
            summary = "채팅 세션 생성",
            description = """
                    새 채팅방을 시작할 때 호출.
                    응답 result.sessionId(UUID v4)를 저장해 두고 이후 메시지 전송에 사용한다.
                    body 불필요, 인증 헤더만 있으면 된다.
                    """)
    public ApiResponseDto<CreateSessionResponse> createSession(@UserCode String username) {
        return ApiResponseDto.onSuccess(createChatSessionUseCase.execute(username));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(
            summary = "채팅 세션 삭제 (hard delete, cascade)",
            description = """
                    채팅방 삭제. 해당 세션에 속한 모든 메시지가 즉시 영구 삭제된다(복구 불가).
                    UI에서는 삭제 확인 다이얼로그를 띄우는 것을 권장.

                    오류:
                      • 4200 CHAT_SESSION_NOT_FOUND  - 존재하지 않는 sessionId
                      • 4201 CHAT_SESSION_NO_PERMISSION  - 다른 사용자의 세션
                    """)
    public ApiResponseDto<Void> deleteSession(
            @UserCode String username,
            @PathVariable String sessionId
    ) {
        deleteChatSessionUseCase.execute(username, sessionId);
        return ApiResponseDto.onSuccess(null);
    }

    @GetMapping("/sessions")
    @Operation(
            summary = "내 채팅방 목록 조회 (최신 활동순)",
            description = """
                    홈/채팅 목록 화면에서 사용. 사용자의 모든 세션을 마지막 메시지 시각 기준 내림차순으로 반환.

                    각 세션 미리보기 항목:
                      • lastMessage          - 마지막 사용자 발화 (마음일기 트리거 세션이면 null)
                      • lastUpdated          - 세션 lastModifiedDate
                      • distortionTags       - 마지막 메시지의 detected_distortion ('없음'이면 빈 배열)
                      • emotion              - 사용자 피드백(feedback_emotion) > 봇 분석(emotion) 우선순위로 노출
                    """)
    public ApiResponseDto<SessionListResponse> getSessions(@UserCode String username) {
        return ApiResponseDto.onSuccess(getChatSessionsUseCase.execute(username));
    }

    @GetMapping("/history/{sessionId}")
    @Operation(
            summary = "채팅 상세 (메시지 페이징)",
            description = """
                    특정 세션의 대화 내용을 페이지 단위(20건)로 시간순 반환.

                    응답 구조 주의:
                      • DB row 1개 = user 발화 + assistant 응답 한 쌍이 펼쳐져 messages 배열에 두 항목으로 노출됨
                      • 같은 한 쌍은 동일한 messageId를 공유 (assistant 행에 피드백 PUT을 쏘면 됨)
                      • 마음일기 트리거(MIND_DIARY) 메시지는 user 발화 없이 assistant 항목만 포함

                    assistant 항목의 도란이 응답 6개 필드 (UI 매핑 가이드):
                      • detected_distortion  → 상단 분류 뱃지 (흑백사고/파국화/긍정 정서 강화 등 11개)
                      • empathy              → 본문 공감 멘트
                      • socratic_question    → '함께 생각해봐요' 카드
                      • analysis             → 'AI 분석 리포트 - 심층 분석' 카드
                      • alternative_thought  → '대안적 사고' 카드
                      • emotion              → 사이드 감정 라벨 (happy/sad/neutral/angry/anxiety/surprise)

                    피드백 필드 (사용자가 PUT 호출 후에만 채워짐):
                      • feedbackEmotion / feedbackDetail / feedbackAt
                    """)
    public ApiResponseDto<ChatHistoryResponse> getHistory(
            @UserCode String username,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "1") int page
    ) {
        return ApiResponseDto.onSuccess(getChatHistoryUseCase.execute(username, sessionId, page));
    }

    @PostMapping("/reframing")
    @Operation(
            summary = "텍스트 채팅 (도란이 동기 응답)",
            description = """
                    텍스트로 도란이와 대화. 서버가 LLM(Gemini 2.5 Pro)을 동기로 호출하므로
                    응답까지 보통 3~10초 소요됨. 클라이언트 타임아웃은 30초 이상 권장.

                    body:
                      • sessionId  (필수) - 미리 만든 세션 UUID
                      • userInput  (필수) - 사용자 발화
                      • emotion    (선택) - 클라이언트가 짐작한 감정 힌트
                                           (happy/sad/neutral/angry/anxiety/surprise)

                    응답: assistant 6개 필드 + 새로 저장된 messageId (피드백 PUT에 사용 가능).

                    Gemini 호출 실패 시 폴백 응답 ("죄송해요, 잠시 생각이 꼬였나 봐요...") 반환.
                    """)
    public ApiResponseDto<ReframingResponse> reframing(
            @UserCode String username,
            @RequestBody @Valid ReframingRequest request
    ) {
        return ApiResponseDto.onSuccess(sendReframingMessageUseCase.execute(username, request));
    }

    @PostMapping("/voice-reframing")
    @Operation(
            summary = "음성 채팅 (감정 분석 결과 반영)",
            description = """
                    음성으로 도란이와 대화. STT 결과 텍스트(userInput)와 함께 사전에 업로드한
                    음성 파일의 voiceId를 보내면, 서버가 해당 voice의 6대 감정 분포 + 세부 감정 top 3을
                    프롬프트에 주입해 더 정확한 답변을 생성한다.

                    권장 흐름:
                      1) 일반 음성 일기 업로드 플로우와 동일하게 voice 등록 (POST /voices/...)
                      2) 분석이 끝난 voiceId를 받아 이 API 호출
                      3) voiceId가 없거나 분석이 미완료여도 OK — 서버가 자동으로 텍스트 모드로 폴백

                    body:
                      • sessionId  (필수)
                      • userInput  (필수) - STT 결과
                      • voiceId    (선택) - 분석된 Voice 엔티티 ID. 없으면 텍스트 모드로 처리

                    응답: 텍스트 채팅과 동일 형상 (도란이 6개 필드 + messageId).
                    """)
    public ApiResponseDto<ReframingResponse> voiceReframing(
            @UserCode String username,
            @RequestBody @Valid VoiceReframingRequest request
    ) {
        return ApiResponseDto.onSuccess(sendVoiceReframingMessageUseCase.execute(username, request));
    }

    @PutMapping("/messages/{messageId}/feedback")
    @Operation(
            summary = "메시지에 '진짜 마음' 피드백 기록",
            description = """
                    봇이 분석한 emotion이 본인 마음과 다를 때 사용자가 직접 입력하는 피드백.
                    원본 bot_response.emotion은 그대로 유지되고, feedback_emotion 컬럼이 별도 저장됨
                    (분석용 데이터로 둘 다 보존).

                    GET /sessions / GET /history 응답에서는 feedback_emotion이 있으면 해당 값을 우선 노출.

                    body:
                      • emotion (필수) - happy / sad / neutral / angry / anxiety / surprise 중 하나
                                          (그 외 값이면 4204 CHAT_FEEDBACK_INVALID_EMOTION)
                      • detail  (선택) - "자세한 이야기" 자유 입력

                    같은 messageId에 다시 호출하면 마지막 값으로 덮어쓰기 + feedback_at 갱신 (수정 가능).

                    오류:
                      • 4202 CHAT_MESSAGE_NOT_FOUND
                      • 4203 CHAT_MESSAGE_NO_PERMISSION  - 다른 사용자 세션의 메시지
                      • 4204 CHAT_FEEDBACK_INVALID_EMOTION
                    """)
    public ApiResponseDto<FeedbackResponse> updateFeedback(
            @UserCode String username,
            @PathVariable Long messageId,
            @RequestBody @Valid FeedbackRequest request
    ) {
        return ApiResponseDto.onSuccess(
                updateMessageFeedbackUseCase.execute(username, messageId, request));
    }
}
