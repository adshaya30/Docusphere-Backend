package com.docusphere.backend.admin.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class MergeTeamsRequest {

    @NotNull(message = "sourceTeamId is required")
    private UUID sourceTeamId;

    @NotNull(message = "targetTeamId is required")
    private UUID targetTeamId;

    @NotBlank(message = "newTeamName is required")
    private String newTeamName;

    @NotNull(message = "newLeaderId is required")
    private Long newLeaderId;

    private boolean moveDocuments = true; // Default to true as requested

    public MergeTeamsRequest() {}

    public UUID getSourceTeamId() { return sourceTeamId; }
    public void setSourceTeamId(UUID sourceTeamId) { this.sourceTeamId = sourceTeamId; }

    public UUID getTargetTeamId() { return targetTeamId; }
    public void setTargetTeamId(UUID targetTeamId) { this.targetTeamId = targetTeamId; }

    public String getNewTeamName() { return newTeamName; }
    public void setNewTeamName(String newTeamName) { this.newTeamName = newTeamName; }

    public Long getNewLeaderId() { return newLeaderId; }
    public void setNewLeaderId(Long newLeaderId) { this.newLeaderId = newLeaderId; }

    public boolean isMoveDocuments() { return moveDocuments; }
    public void setMoveDocuments(boolean moveDocuments) { this.moveDocuments = moveDocuments; }
}
