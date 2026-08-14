package com.docusphere.backend.notification.event;

import com.docusphere.backend.notification.entity.NotificationAudience;
import com.docusphere.backend.notification.entity.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for publishing notification events throughout the application.
 * Inject this service and use it to trigger notifications for various actions.
 * 
 * Example usage:
 * notificationEventPublisher.publishNotificationEvent(
 *     userId, 
 *     NotificationType.DOCUMENT_SHARED, 
 *     "document", 
 *     documentId,
 *     "Document has been shared with you",
 *     "/documents/" + documentId
 * );
 */
@Service
@Slf4j
public class NotificationEventPublisher {

    private final ApplicationEventPublisher eventPublisher;

    public NotificationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Publish a notification event
     */
    public void publishNotificationEvent(UUID recipientUserId, NotificationType type,
                                        String relatedEntityType, UUID relatedEntityId) {
        publishNotificationEvent(recipientUserId, type, null, null,
            relatedEntityType, relatedEntityId, null, null);
    }

    /**
     * Publish a notification event with title and message
     */
    public void publishNotificationEvent(UUID recipientUserId, NotificationType type,
                                        String title, String message,
                                        String relatedEntityType, UUID relatedEntityId) {
        publishNotificationEvent(recipientUserId, type, title, message,
            relatedEntityType, relatedEntityId, null, null);
    }

    /**
     * Publish a notification event with all parameters
     */
    public void publishNotificationEvent(UUID recipientUserId, NotificationType type,
                                        String title, String message,
                                        String relatedEntityType, UUID relatedEntityId,
                                        String metadata, String actionUrl) {
        publishNotificationEvent(recipientUserId, type, title, message,
            relatedEntityType, relatedEntityId, metadata, actionUrl, NotificationAudience.USER);
    }

    public void publishNotificationEvent(UUID recipientUserId, NotificationType type,
                                        String title, String message,
                                        String relatedEntityType, UUID relatedEntityId,
                                        String metadata, String actionUrl,
                                        NotificationAudience audience) {
        NotificationAudience resolved = NotificationAudience.resolve(type, audience);

        NotificationEvent event = NotificationEvent.builder()
            .recipientUserId(recipientUserId)
            .type(type)
            .audience(resolved)
            .title(title)
            .message(message)
            .relatedEntityType(relatedEntityType)
            .relatedEntityId(relatedEntityId)
            .metadata(metadata)
            .actionUrl(actionUrl)
            .timestamp(System.currentTimeMillis())
            .build();

        eventPublisher.publishEvent(event);
        log.debug("Notification event published for user: {}, type: {}, audience: {}",
            recipientUserId, type, resolved);
    }

    /**
     * Publish notification to multiple users
     */
    public void publishNotificationEventToUsers(Iterable<UUID> userIds, NotificationType type,
                                               String title, String message,
                                               String relatedEntityType, UUID relatedEntityId,
                                               String metadata, String actionUrl) {
        for (UUID userId : userIds) {
            publishNotificationEvent(userId, type, title, message,
                relatedEntityType, relatedEntityId, metadata, actionUrl);
        }
    }
}
