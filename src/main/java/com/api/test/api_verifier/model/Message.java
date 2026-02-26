package com.api.test.api_verifier.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String conversationId;
    private String role; // system | user | model
    @Column(columnDefinition = "CLOB")
    private String content;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId(){
        return id;
    }
    public String getConversationId() {
        return conversationId;
    }
    public String getRole(){
        return role;
    }
    public String getContent(){
        return content;
    }
    public LocalDateTime getCreatedAt(){
        return createdAt;
    }
    public void setId(Long id){
        this.id=id;
    }
    public void setConversationId(String conversationId){
        this.conversationId=conversationId;
    }
    public void setRole(String role){
        this.role=role;
    }
    public void setContent(String content){
        this.content=content;
    }
    public void setCreatedAt(LocalDateTime createdAt){
        this.createdAt=createdAt;
    }

}