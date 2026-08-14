package com.docusphere.backend.chat.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class TeamChatMessageResponse {
    private UUID id;
    private UUID teamId;
    private Long senderId;
    private String senderName;
    private String content;
    private TeamChatMessageRequest.MessageType messageType;
    private UUID documentId;
    private List<Long> mentionUserIds;
    private LocalDateTime createdAt;
    private boolean isEdited;
    private LocalDateTime editedAt;
    private List<Long> seenBy;
    private List<Long> deliveredTo;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTeamId() {
        return teamId;
    }

    public void setTeamId(UUID teamId) {
        this.teamId = teamId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public TeamChatMessageRequest.MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(TeamChatMessageRequest.MessageType messageType) {
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isEdited() {
        return isEdited;
    }

    public void setEdited(boolean edited) {
        isEdited = edited;
    }

    public LocalDateTime getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(LocalDateTime editedAt) {
        this.editedAt = editedAt;
    }

    public List<Long> getSeenBy() {
        return seenBy;
    }

    public void setSeenBy(List<Long> seenBy) {
        this.seenBy = seenBy;
    }

    public List<Long> getDeliveredTo() {
        return deliveredTo;
    }

    public void setDeliveredTo(List<Long> deliveredTo) {
        this.deliveredTo = deliveredTo;
    }
}
