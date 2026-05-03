package com.docusphere.backend.team.dto;



import jakarta.validation.constraints.NotNull;

/**
 * Request to atomically transfer leadership from the current leader
 * to an existing team member.
 *
 * Flow (enforced in AdminTeamService):
 *   1. newLeaderId must be a current MEMBER or MANAGER of the team
 *   2. Old leader → MEMBER
 *   3. New leader → LEADER
 *   Both steps in a single @Transactional call — no intermediate state.
 */
public class TransferLeaderRequest {

    @NotNull(message = "newLeaderId is required")
    private Long newLeaderId;

    public TransferLeaderRequest() {}

    public Long getNewLeaderId() { return newLeaderId; }
    public void setNewLeaderId(Long newLeaderId) { this.newLeaderId = newLeaderId; }
}