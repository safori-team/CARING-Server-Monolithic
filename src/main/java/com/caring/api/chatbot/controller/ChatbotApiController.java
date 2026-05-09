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
    public ApiResponseDto<CreateSessionResponse> createSession(@UserCode String username) {
        return ApiResponseDto.onSuccess(createChatSessionUseCase.execute(username));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponseDto<Void> deleteSession(
            @UserCode String username,
            @PathVariable String sessionId
    ) {
        deleteChatSessionUseCase.execute(username, sessionId);
        return ApiResponseDto.onSuccess(null);
    }

    @GetMapping("/sessions")
    public ApiResponseDto<SessionListResponse> getSessions(@UserCode String username) {
        return ApiResponseDto.onSuccess(getChatSessionsUseCase.execute(username));
    }

    @GetMapping("/history/{sessionId}")
    public ApiResponseDto<ChatHistoryResponse> getHistory(
            @UserCode String username,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "1") int page
    ) {
        return ApiResponseDto.onSuccess(getChatHistoryUseCase.execute(username, sessionId, page));
    }

    @PostMapping("/reframing")
    public ApiResponseDto<ReframingResponse> reframing(
            @UserCode String username,
            @RequestBody @Valid ReframingRequest request
    ) {
        return ApiResponseDto.onSuccess(sendReframingMessageUseCase.execute(username, request));
    }

    @PostMapping("/voice-reframing")
    public ApiResponseDto<ReframingResponse> voiceReframing(
            @UserCode String username,
            @RequestBody @Valid VoiceReframingRequest request
    ) {
        return ApiResponseDto.onSuccess(sendVoiceReframingMessageUseCase.execute(username, request));
    }

    @PutMapping("/messages/{messageId}/feedback")
    public ApiResponseDto<FeedbackResponse> updateFeedback(
            @UserCode String username,
            @PathVariable Long messageId,
            @RequestBody @Valid FeedbackRequest request
    ) {
        return ApiResponseDto.onSuccess(
                updateMessageFeedbackUseCase.execute(username, messageId, request));
    }
}
