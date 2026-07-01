package com.docusphere.backend.chat.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class TeamChatMessageRequest {

    @NotBlank(message = "Message content is required")
    private String content;

    @NotNull(message = "Message type is required")
    private MessageType messageType = MessageType.TEXT;

    private UUID documentId;

    private List<Long> mentionUserIds;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public void setDocumentId(UUID documentId) {
        this.documentId = documentId;
    }

    public List<Long> getMentionUserIds() {
        return mentionUserIds;
    }

    public void setMentionUserIds(List<Long> mentionUserIds) {
        this.mentionUserIds = mentionUserIds;
    }

    public enum MessageType {
        TEXT,
        CHANGE_REQUEST
    }
}
