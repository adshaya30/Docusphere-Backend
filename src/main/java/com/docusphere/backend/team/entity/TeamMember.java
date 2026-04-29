package com.docusphere.backend.team.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "team_members")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Many members belong to one team
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private TeamRole role;

    @Column(name = "last_seen", nullable = false)
    private LocalDateTime lastSeen;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "permissions", length = 255)
    private String permissions;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    // Auto timestamps
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        if (this.joinedAt == null) {
            this.joinedAt = LocalDateTime.now();
        }

        if (this.lastSeen == null) {
            this.lastSeen = LocalDateTime.now();
        }

        if (this.active == null) {
            this.active = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Team getTeam() { return team; }
    public void setTeam(Team team) { this.team = team; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public TeamRole getRole() { return role; }
    public void setRole(TeamRole role) { this.role = role; }
    public LocalDateTime getLastSeen() { return lastSeen; }
    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
