package com.docusphere.backend.team.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "team",
    uniqueConstraints = {
        @UniqueConstraint(name = "team_team_name_key", columnNames = "team_name")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "team_name", nullable = false)
    private String teamName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "member_count", nullable = false)
    private Integer memberCount = 0;

    @Column(name = "document_count", nullable = false)
    private Integer documentCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();

        if (this.memberCount == null) {
            this.memberCount = 0;
        }

        if (this.documentCount == null) {
            this.documentCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    public Integer getDocumentCount() { return documentCount; }
    public void setDocumentCount(Integer documentCount) { this.documentCount = documentCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
