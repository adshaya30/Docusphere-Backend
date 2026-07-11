package com.docusphere.backend.notification.event;

import com.docusphere.backend.notification.entity.NotificationAudience;
import com.docusphere.backend.notification.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Generic notification event that can be published throughout the application.
 * This event is listened to by the NotificationEventListener which creates notifications.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEvent {

    /**
     * The user ID who should receive the notification
     */
    private UUID recipientUserId;

    /**
     * Type of notification
     */
    private NotificationType type;

    /**
     * USER for end-user inbox, ADMIN for admin panel inbox
     */
    @Builder.Default
    private NotificationAudience audience = NotificationAudience.USER;

    /**
     * Notification title
     */
    private String title;

    /**
     * Notification message
     */
    private String message;

    /**
     * ID of the entity that triggered the notification (e.g., document ID, team ID)
     */
    private UUID relatedEntityId;

    /**
     * Type of the related entity (DOCUMENT, TEAM, ACTION, etc.)
     */
    private String relatedEntityType;

    /**
     * Optional metadata in JSON format
     */
    private String metadata;

    /**
     * Optional action URL for the frontend to navigate to
     */
    private String actionUrl;

    /**
     * Timestamp when the event was created
     */
    private Long timestamp;

    public NotificationEvent(UUID recipientUserId, NotificationType type, String relatedEntityType, UUID relatedEntityId) {
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.relatedEntityType = relatedEntityType;
        this.relatedEntityId = relatedEntityId;
        this.timestamp = System.currentTimeMillis();
    }
}
