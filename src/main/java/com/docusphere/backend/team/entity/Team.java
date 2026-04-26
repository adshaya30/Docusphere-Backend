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
}
