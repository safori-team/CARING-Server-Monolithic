package com.caring.common.event;

/**
 * 마음일기 음성 감정 분석이 완료되었을 때 발행되는 이벤트.
 * Voice 도메인 → Chatbot 도메인 (선제 대화 트리거) 등 후속 작업이 구독한다.
 */
public record VoiceAnalysisCompletedEvent(Long voiceId) {}
