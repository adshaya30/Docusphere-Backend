package com.docusphere.backend.admin.team.dto;

import java.time.LocalDateTime;
import java.util.UUID;

// ── Admin view of a team member (includes join date + role context) ──────────
public class AdminMemberView {

    private UUID membershipId;
    private Long userId;
    private String fullName;
    private String email;
    private String role;
    private UUID teamId;
    private String teamName;
    private LocalDateTime joinedAt;
    private String status;

    public AdminMemberView() {}

    public UUID getMembershipId() { return membershipId; }
    public void setMembershipId(UUID membershipId) { this.membershipId = membershipId; }

    public UUID getId() { return membershipId; }
    
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}