package com.docusphere.backend.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service for scheduling notification cleanup tasks.
 */
@Service
@Slf4j
public class NotificationCleanupService {

    private final NotificationService notificationService;

    @Value("${app.notification.cleanup.days:30}")
    private int cleanupDays;

    public NotificationCleanupService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Scheduled job to clean up old archived notifications
     * Runs daily at 3 AM by default (configurable via app.notification.cleanup.cron)
     */
    @Scheduled(cron = "${app.notification.cleanup.cron:0 0 3 * * *}")
    public void cleanupOldArchivedNotifications() {
        try {
            log.info("Starting cleanup of old archived notifications (older than {} days)", cleanupDays);
            notificationService.deleteOldArchivedNotifications(cleanupDays);
            log.info("Completed cleanup of old archived notifications");
        } catch (Exception e) {
            log.error("Error during notification cleanup", e);
        }
    }
}
