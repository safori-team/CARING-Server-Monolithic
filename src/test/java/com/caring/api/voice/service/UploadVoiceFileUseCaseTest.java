package com.caring.api.voice.service;

import com.caring.domain.question.entity.QuestionCategory;
import com.caring.domain.user.adaptor.UserAdaptor;
import com.caring.domain.user.entity.User;
import com.caring.domain.voice.entity.Voice;
import com.caring.domain.voice.service.VoiceDomainService;
import com.caring.infra.ai.gemini.GeminiVoiceAnalyzer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UploadVoiceFileUseCaseTest {

    @Mock UserAdaptor userAdaptor;
    @Mock VoiceDomainService voiceDomainService;
    @Mock GeminiVoiceAnalyzer geminiVoiceAnalyzer;
    @Mock User user;
    @Mock Voice voice;

    @Test
    @DisplayName("업로드 성공 - Gemini 분석 비동기 트리거")
    void execute_triggersGeminiAnalysisAsync() {
        String username = "testUser";
        String voiceKey = "voices/testUser/uuid.m4a";
        QuestionCategory category = QuestionCategory.EMOTION;
        int index = 0;

        given(userAdaptor.queryUserByUsername(username)).willReturn(user);
        given(voiceDomainService.uploadVoiceFile(user, voiceKey)).willReturn(voice);
        given(voice.getId()).willReturn(1L);

        UploadVoiceFileUseCase useCase = new UploadVoiceFileUseCase(
                userAdaptor, voiceDomainService, geminiVoiceAnalyzer);

        Long voiceId = useCase.execute(username, category, index, voiceKey, "2026-04-04T00:00:00");

        assertThat(voiceId).isEqualTo(1L);
        verify(geminiVoiceAnalyzer).analyzeAsync(1L, voiceKey);
    }

    @Test
    @DisplayName("voiceId 즉시 반환 - 분석은 비동기")
    void execute_returnsVoiceIdImmediately() {
        String username = "testUser";
        String voiceKey = "voices/testUser/uuid.m4a";

        given(userAdaptor.queryUserByUsername(username)).willReturn(user);
        given(voiceDomainService.uploadVoiceFile(user, voiceKey)).willReturn(voice);
        given(voice.getId()).willReturn(42L);

        UploadVoiceFileUseCase useCase = new UploadVoiceFileUseCase(
                userAdaptor, voiceDomainService, geminiVoiceAnalyzer);

        Long voiceId = useCase.execute(username, QuestionCategory.EMOTION, 0, voiceKey, "2026-04-04T00:00:00");

        assertThat(voiceId).isEqualTo(42L);
    }
}
