package com.caring.domain.chatbot.entity;

public enum MessageOrigin {
    USER_TEXT,    // 텍스트 채팅
    USER_VOICE,   // 음성 채팅 (voice_id 첨부)
    MIND_DIARY    // 마음일기 트리거 (사용자 발화 없이 봇이 먼저 보낸 메시지)
}
