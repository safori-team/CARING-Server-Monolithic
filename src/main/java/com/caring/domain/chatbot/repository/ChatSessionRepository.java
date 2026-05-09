package com.caring.domain.chatbot.repository;

import com.caring.domain.chatbot.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    List<ChatSession> findByUser_UsernameOrderByLastModifiedDateDesc(String username);
}
