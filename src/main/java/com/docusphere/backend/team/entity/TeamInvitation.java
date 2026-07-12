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

    @Column(name = "inviter_id")
    private Long inviterId;

    @Column(name = "inviter_name")
    private String inviterName;

    @PrePersist
    protected void onCreate() {
        this.invitedAt = LocalDateTime.now();
    }
}
