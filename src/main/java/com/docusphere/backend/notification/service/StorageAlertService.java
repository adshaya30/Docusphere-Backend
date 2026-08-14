package com.docusphere.backend.notification.service;

import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.notification.entity.NotificationAudience;
import com.docusphere.backend.notification.entity.NotificationType;
import com.docusphere.backend.notification.event.NotificationEventPublisher;
import com.docusphere.backend.notification.util.NotificationUserIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageAlertService {

    private final NotificationEventPublisher publisher;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    @Value("${docusphere.storage.quota-bytes:1073741824}") // 1 GB default
    private long storageQuotaBytes;

    // Simple in-memory tracker of the last warned threshold (e.g. 70, 90, 100)
    // to avoid spamming the database on every single upload.
    private int lastFiredThreshold = 0;

    public void checkAndAlertStorageUsage() {
        try {
            Long totalBytes = documentRepository.sumTotalStorageBytes();
            if (totalBytes == null || totalBytes == 0 || storageQuotaBytes <= 0) return;

            double usagePct = (totalBytes * 100.0) / storageQuotaBytes;
            log.debug("Checking storage usage: {:.1f}% ({} / {} bytes)", usagePct, totalBytes, storageQuotaBytes);

            int currentThreshold = 0;
            if (usagePct >= 100.0) {
                currentThreshold = 100;
            } else if (usagePct >= 90.0) {
                currentThreshold = 90;
            } else if (usagePct >= 70.0) {
                currentThreshold = 70;
            }

            if (currentThreshold > 0 && currentThreshold != lastFiredThreshold) {
                lastFiredThreshold = currentThreshold;
                fireStorageAlert(currentThreshold, usagePct, totalBytes);
            } else if (currentThreshold == 0) {
                // Reset tracker if usage drops below 70%
                lastFiredThreshold = 0;
            }
        } catch (Exception ex) {
            log.error("Failed to evaluate storage warning: {}", ex.getMessage());
        }
    }

    private void fireStorageAlert(int thresholdPct, double actualPct, long totalBytes) {
        String usedFormatted = formatBytes(totalBytes);
        String quotaFormatted = formatBytes(storageQuotaBytes);

        String title;
        String message;
        NotificationType type = NotificationType.ADMIN_STORAGE_WARNING;

        if (thresholdPct >= 100) {
            title = "🚨 Storage Limit Reached (100% capacity)";
            message = String.format("Platform storage has reached 100%% capacity (%s of %s used). Uploads may fail. Please increase the quota.", usedFormatted, quotaFormatted);
        } else if (thresholdPct >= 90) {
            title = "🚨 Storage Critical (" + String.format("%.0f%%", actualPct) + " used)";
            message = String.format("Platform storage is at %.1f%% capacity (%s of %s used). Consider cleaning up old documents or increasing the quota.", actualPct, usedFormatted, quotaFormatted);
        } else {
            title = "⚠️ Storage Warning (" + String.format("%.0f%%", actualPct) + " used)";
            message = String.format("Platform storage is at %.1f%% capacity (%s of %s used).", actualPct, usedFormatted, quotaFormatted);
        }

        String metadata = String.format(
                "{\"scope\":\"system\",\"category\":\"storage\",\"action\":\"limit_warning\"," +
                "\"threshold\":%d,\"usagePct\":%.1f,\"usedBytes\":%d,\"quotaBytes\":%d}",
                thresholdPct, actualPct, totalBytes, storageQuotaBytes);

        log.warn("STORAGE_WARNING: {}% threshold crossed. {}", thresholdPct, message);
        
        List<Long> adminIds = userRepository.findAllAdminUserIds();
        if (adminIds == null || adminIds.isEmpty()) {
            log.warn("No admin users found to notify for storage warning");
            return;
        }

        for (Long adminId : adminIds) {
            try {
                publisher.publishNotificationEvent(
                        NotificationUserIds.fromUserId(adminId),
                        type, title, message,
                        "system", null,
                        metadata, "/admin/dashboard",
                        NotificationAudience.ADMIN
                );
            } catch (Exception ex) {
                log.error("Failed to send storage warning to admin {}: {}", adminId, ex.getMessage());
            }
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1_073_741_824L) return String.format("%.2f GB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576L)     return String.format("%.1f MB", bytes / 1_048_576.0);
        if (bytes >= 1_024L)         return String.format("%.0f KB", bytes / 1_024.0);
        return bytes + " B";
    }
}
