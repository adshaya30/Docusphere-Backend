package com.docusphere.backend.chat.dto;

import java.util.List;
import java.util.UUID;

/**
 * Snapshot of receipt columns after an update (broadcast to team topic).
 */
public class TeamChatReceiptUpdate {

    private UUID messageId;
    private List<Long> deliveredTo;
    private List<Long> seenBy;

    public UUID getMessageId() {
        return messageId;
    }

    public void setMessageId(UUID messageId) {
        this.messageId = messageId;
    }

    public List<Long> getDeliveredTo() {
        return deliveredTo;
    }

    public void setDeliveredTo(List<Long> deliveredTo) {
        this.deliveredTo = deliveredTo;
    }

    public List<Long> getSeenBy() {
        return seenBy;
    }

    public void setSeenBy(List<Long> seenBy) {
        this.seenBy = seenBy;
    }
}
