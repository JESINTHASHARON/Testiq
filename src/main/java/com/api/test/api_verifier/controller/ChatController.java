package com.api.test.api_verifier.controller;

import com.api.test.api_verifier.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping("/{conversationId}")
    public String chat(
            @PathVariable String conversationId,
            @RequestParam String message
    ) throws Exception {
        return chatService.sendMessage(conversationId, message);
    }
}