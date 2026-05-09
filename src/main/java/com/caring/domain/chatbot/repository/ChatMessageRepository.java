package com.caring.domain.chatbot.repository;

import com.caring.domain.chatbot.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findBySession_IdOrderByCreatedDateDesc(String sessionId, Pageable pageable);

    long countBySession_Id(String sessionId);

    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId ORDER BY m.createdDate DESC")
    List<ChatMessage> findRecentBySessionId(String sessionId, Pageable pageable);

    Optional<ChatMessage> findTopBySession_IdOrderByCreatedDateDesc(String sessionId);
}
