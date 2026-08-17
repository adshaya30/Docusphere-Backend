package com.docusphere.backend.chat.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Realtime receipt broadcast (same channel as chat messages). Clients merge into local state.
 */
public class TeamChatReceiptBatchEvent {

    private String type = "RECEIPT_BATCH";
    private List<TeamChatReceiptUpdate> updates = new ArrayList<>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<TeamChatReceiptUpdate> getUpdates() {
        return updates;
    }

    public void setUpdates(List<TeamChatReceiptUpdate> updates) {
        this.updates = updates != null ? updates : new ArrayList<>();
    }
}
