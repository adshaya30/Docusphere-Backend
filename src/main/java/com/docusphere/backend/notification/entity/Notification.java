package com.docusphere.backend.notification.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a notification in the Docusphere system.
 * Notifications are sent to users for various events such as team invitations,
 * document sharing, and action assignments.
 */
@Entity
@Table(name = "notification", indexes = {
    @Index(name = "idx_notification_user_id", columnList = "user_id"),
    @Index(name = "idx_notification_status", columnList = "status"),
    @Index(name = "idx_notification_created_at", columnList = "created_at"),
    @Index(name = "idx_notification_user_status", columnList = "user_id, status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false)
    private NotificationAudience audience = NotificationAudience.USER;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata; // JSON string for additional data

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status = NotificationStatus.UNREAD;

    @Column(name = "related_entity_id")
    private UUID relatedEntityId; // ID of the entity that triggered the notification (document, team, etc.)

    @Column(name = "related_entity_type")
    private String relatedEntityType; // Type of entity (DOCUMENT, TEAM, ACTION, etc.)

    @Column(name = "action_url")
    private String actionUrl; // Optional URL for action link

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Mark this notification as read
     */
    public void markAsRead() {
        this.status = NotificationStatus.READ;
        this.readAt = LocalDateTime.now();
    }

    /**
     * Mark this notification as unread
     */
    public void markAsUnread() {
        this.status = NotificationStatus.UNREAD;
        this.readAt = null;
    }

    /**
     * Mark this notification as archived
     */
    public void archive() {
        this.status = NotificationStatus.ARCHIVED;
        this.archivedAt = LocalDateTime.now();
    }

    /**
     * Check if notification is unread
     */
    public boolean isUnread() {
        return status == NotificationStatus.UNREAD;
    }

    /**
     * Check if notification is read
     */
    public boolean isRead() {
        return status == NotificationStatus.READ;
    }
}
