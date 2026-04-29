package com.docusphere.backend.team.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_activities")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "activity_type")
    private String activityType;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @PrePersist
    protected void onCreate() {
        this.occurredAt = LocalDateTime.now();
    }
}