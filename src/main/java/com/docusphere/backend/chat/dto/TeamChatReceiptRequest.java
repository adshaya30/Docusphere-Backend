package com.docusphere.backend.chat.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Client ack for delivery (message reached this member) or read (member viewed the message).
 */
public class TeamChatReceiptRequest {

    @NotNull
    private UUID messageId;

    /**
     * DELIVERED — append user to {@code delivered_to}.
     * SEEN — append user to {@code seen_by} (ignored for the sender's own messages).
     */
    @NotBlank
    private String kind;

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }
}
