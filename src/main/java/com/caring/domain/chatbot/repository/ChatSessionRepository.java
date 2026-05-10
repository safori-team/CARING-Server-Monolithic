package com.caring.domain.chatbot.repository;

import com.caring.domain.chatbot.entity.ChatSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    Page<ChatSession> findByUser_UsernameOrderByLastModifiedDateDesc(String username, Pageable pageable);
}

