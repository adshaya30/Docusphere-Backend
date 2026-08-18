package com.docusphere.backend.team.dto;



import java.time.LocalDateTime;
import java.util.UUID;

public class TeamMemberDto {

    private UUID id;
    private Long userId;
    private UUID teamId;
    private String teamName;
    private String fullName;
    private String email;
    private String role;
    private LocalDateTime joinedAt;
  /** When false, the member is blocked from team chat. */
    private Boolean active;
    private String status;

    public TeamMemberDto() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}