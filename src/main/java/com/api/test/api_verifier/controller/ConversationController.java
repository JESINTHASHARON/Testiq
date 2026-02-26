package com.api.test.api_verifier.controller;

import com.api.test.api_verifier.model.Conversation;
import com.api.test.api_verifier.service.ConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/conversation")
public class ConversationController {

    @Autowired
    private ConversationService conversationService;

    @PostMapping("/new")
    public Conversation newConversation() {
        return conversationService.createEmptyConversation();
    }

    @PutMapping("/{conversationId}/updateTitle")
    public String updateTitle(@PathVariable String conversationId, @RequestParam String title){
        conversationService.updateTitle(conversationId, title);
        return "Title Updated Successfully";
    }

    @PostMapping("/{conversationId}")
    public String deleteConversation(@PathVariable String conversationId){
        conversationService.deleteConversation(conversationId);
        return "Conversation deleted";
    }

    @GetMapping("/{conversationId}")
    public Conversation getConversationById(@PathVariable String conversationId){
        return conversationService.getConversationById(conversationId);
    }

    @GetMapping
    public List<Conversation> getAllConversation(){
        return conversationService.getAllConversation();
    }
}