package com.docusphere.backend.chat.dto;

import java.util.UUID;

public class DeleteMessageEvent {
    private UUID messageId;
    private Long deletedBy;
    private String scope; // "for-me" or "for-everyone"

    public DeleteMessageEvent() {}

    public DeleteMessageEvent(UUID messageId, Long deletedBy, String scope) {
        this.messageId = messageId;
        this.deletedBy = deletedBy;
        this.scope = scope;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public Long getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(Long deletedBy) {
        this.deletedBy = deletedBy;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
