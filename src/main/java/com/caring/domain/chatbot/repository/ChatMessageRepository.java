package com.caring.domain.chatbot.repository;

import com.caring.domain.chatbot.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findBySession_IdOrderByCreatedDateDesc(String sessionId, Pageable pageable);

    long countBySession_Id(String sessionId);

    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId ORDER BY m.createdDate DESC")
    List<ChatMessage> findRecentBySessionId(@Param("sessionId") String sessionId, Pageable pageable);

    Optional<ChatMessage> findTopBySession_IdOrderByCreatedDateDesc(String sessionId);

    /**
     * 권한 검증용 fetch join — message → session → user를 한 번의 쿼리로.
     * verifyOwnership에서 lazy 추가 쿼리 2회를 방지.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            JOIN FETCH m.session s
            JOIN FETCH s.user
            WHERE m.id = :messageId
            """)
    Optional<ChatMessage> findByIdWithSessionAndUser(@Param("messageId") Long messageId);

    /**
     * 여러 세션의 최신 메시지를 한 번에 조회 (N+1 방지).
     * 세션별로 createdDate가 가장 큰 메시지 한 건씩 반환.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.session.id IN :sessionIds
              AND m.createdDate = (
                  SELECT MAX(m2.createdDate) FROM ChatMessage m2
                  WHERE m2.session.id = m.session.id
              )
            """)
    List<ChatMessage> findLatestBySessionIds(@Param("sessionIds") List<String> sessionIds);
}
