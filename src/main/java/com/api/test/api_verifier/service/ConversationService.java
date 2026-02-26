package com.api.test.api_verifier.service;

import com.api.test.api_verifier.model.Conversation;
import com.api.test.api_verifier.repository.ConversationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ConversationService {

    @Autowired
    private ConversationRepository conversationRepository;

    public Conversation createEmptyConversation() {
        Conversation conv = new Conversation();
        conv.setTitle("New Conversation");
        return conversationRepository.save(conv);
    }

    public void deleteConversation(String conversationId){
        Conversation conv=conversationRepository.findById(conversationId).orElse(null);
        if(conv!=null){
            conversationRepository.delete(conv);
        }
    }

    public void updateTitle(String conversationId,String title){
        Conversation conv = conversationRepository.findById(conversationId).orElseThrow(()-> new RuntimeException("Conversation not found"));

        conv.setTitle(title);
        conv.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conv);
    }
    public void updateTitleFromFirstMessage(String conversationId, String message) {

        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        if ("New Conversation".equals(conv.getTitle())) {
            conv.setTitle(generateTitleFromMessage(message));
            conv.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conv);
        }
    }

    private String generateTitleFromMessage(String message) {

        if (message == null || message.isBlank()) {
            return "New Conversation";
        }

        String cleaned = message.replaceAll("\\n", " ").trim();
        String[] words = cleaned.split("\\s+");

        int limit = Math.min(words.length, 6);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            sb.append(words[i]).append(" ");
        }
        return sb.toString().trim();
    }
}