package com.api.test.api_verifier.repository;

import com.api.test.api_verifier.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository
        extends JpaRepository<Message, Long> {

    List<Message> findTop20ByConversationIdOrderByCreatedAtDesc(String conversationId);
}