package com.docusphere.backend.chat.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TeamChatReceiptBatchRequest {

    @NotNull
    @Valid
    @Size(max = 80)
    private List<TeamChatReceiptRequest> items = new ArrayList<>();

    public List<TeamChatReceiptRequest> getItems() {
        return items;
    }

    public void setItems(List<TeamChatReceiptRequest> items) {
        this.items = items != null ? items : new ArrayList<>();
    }
}
