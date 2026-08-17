package com.docusphere.backend.notification.event;

import com.docusphere.backend.notification.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;

/**
 * Creates notifications when event is published.
 * Uses Propagation.REQUIRES_NEW so DB operations run in their own transaction
 * and exceptions are caught so DB errors never roll back business actions.
 */
@Component
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onNotificationEvent(NotificationEvent event) {
        try {
            log.info("Processing notification event for user: {}, type: {}, audience: {}",
                event.getRecipientUserId(), event.getType(), event.getAudience());

            notificationService.createNotification(
                event.getRecipientUserId(),
                event.getType(),
                event.getAudience(),
                event.getTitle(),
                event.getMessage(),
                event.getRelatedEntityId(),
                event.getRelatedEntityType(),
                event.getMetadata(),
                event.getActionUrl()
            );
        } catch (Exception e) {
            log.error("Error processing notification event for user {} type {}",
                event.getRecipientUserId(), event.getType(), e);
        }
    }
}
