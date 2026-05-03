package com.docusphere.backend.team.dto;



import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// ── Add member to a team ─────────────────────────────────────────────────────
// Used by both AdminTeamService and UserTeamService (leader adding members)
public class AddMemberRequest {

    private Long userId;

    private String email;

    /**
     * Accepted values: MEMBER, MANAGER
     * LEADER cannot be assigned via this request — use TransferLeaderRequest
     */
    @NotNull(message = "role is required")
    @Pattern(regexp = "MEMBER|MANAGER|LEADER", message = "role must be MEMBER, MANAGER or LEADER")
    private String role;

    public AddMemberRequest() {}

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}