package com.docusphere.backend.team.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "team_invitations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private TeamRole role;

    @Column(name = "invited_at", nullable = false)
    private LocalDateTime invitedAt;

    @PrePersist
    protected void onCreate() {
        this.invitedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }
    public TeamRole getRole() { return role; }
    public void setRole(TeamRole role) { this.role = role; }
    public LocalDateTime getInvitedAt() { return invitedAt; }
    public void setInvitedAt(LocalDateTime invitedAt) { this.invitedAt = invitedAt; }
}
