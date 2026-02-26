package com.api.test.api_verifier.repository;

import com.api.test.api_verifier.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository
        extends JpaRepository<Conversation, String> {
}