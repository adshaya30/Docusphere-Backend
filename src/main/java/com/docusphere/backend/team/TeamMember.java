package com.docusphere.backend.team;

import com.docusphere.backend.authentication.entity.User;
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
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "last_seen", nullable = false)
    private LocalDateTime lastSeen;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    // 🔥 Enum INSIDE entity
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TeamRole role;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 🔁 timestamps
    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.lastSeen = LocalDateTime.now();

        // default role if not set
        if (this.role == null) {
            this.role = TeamRole.MEMBER;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 🔥 Enum INSIDE SAME FILE
    public enum TeamRole {
        MEMBER,
        MANAGER,
        LEADER
    }
}
