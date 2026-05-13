package com.caring.domain.chatbot.adaptor;

import com.caring.domain.chatbot.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ChatMessageAdaptor {
    ChatMessage queryById(Long messageId);

    /** 권한 검증을 위해 message → session → user를 fetch join으로 한 번에 조회. */
    ChatMessage queryByIdWithSessionAndUser(Long messageId);

    Page<ChatMessage> queryBySessionId(String sessionId, Pageable pageable);
    List<ChatMessage> queryRecentBySessionId(String sessionId, int limit);
    long countBySessionId(String sessionId);
    Optional<ChatMessage> queryLatestBySessionId(String sessionId);

    /** 여러 세션의 최신 메시지 한 번에 조회 — N+1 방지. */
    Map<String, ChatMessage> queryLatestBySessionIds(List<String> sessionIds);

    ChatMessage save(ChatMessage message);
}
