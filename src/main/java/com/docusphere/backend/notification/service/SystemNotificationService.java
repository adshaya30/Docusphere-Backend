package com.docusphere.backend.notification.service;

import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.notification.entity.NotificationAudience;
import com.docusphere.backend.notification.entity.NotificationType;
import com.docusphere.backend.notification.event.NotificationEventPublisher;
import com.docusphere.backend.notification.util.NotificationUserIds;
import com.docusphere.backend.authentication.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates system-level admin notifications.
 *
 * <p>Unlike {@link AdminNotificationHelper} which logs admin <em>actions</em>,
 * this service generates <em>platform health alerts</em> — analogous to
 * Supabase's storage-limit warnings or security alerts.</p>
 *
 * <h3>Supported alerts:</h3>
 * <ul>
 *   <li><b>Bulk Deletion Alert</b> — fired inside
 *       {@link com.docusphere.backend.document.service.DocumentService#deleteById}
 *       when a user deletes ≥ {@code bulkDeletionThreshold} documents in
 *       {@code bulkDeletionWindowMinutes} minutes.</li>
 *   <li><b>Storage Warning</b> — fired inside
 *       {@link com.docusphere.backend.upload.service.DocumentUploadService}
 *       (after every successful upload) when total platform bytes cross 70 % or 90 %
 *       of {@code storageQuotaBytes}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemNotificationService {

    private final NotificationEventPublisher publisher;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;

    // In-memory sliding window: userId → deque of deletion timestamps
    private final ConcurrentHashMap<Long, Deque<Instant>> deletionWindows = new ConcurrentHashMap<>();

    // ─── Configuration ───────────────────────────────────────────────────────────

    /** Platform storage quota in bytes. Default: 1 GB (Supabase free tier). */
    @Value("${docusphere.storage.quota-bytes:1073741824}")
    private long storageQuotaBytes;

    /** How many deletions within the window trigger an admin alert. */
    @Value("${docusphere.alert.bulk-deletion-threshold:5}")
    private int bulkDeletionThreshold;

    /** Time window (minutes) in which bulk-deletion is detected. */
    @Value("${docusphere.alert.bulk-deletion-window-minutes:10}")
    private int bulkDeletionWindowMinutes;

    // ─── Bulk Deletion Alert ─────────────────────────────────────────────────────

    /**
     * Call this after every hard-delete of a document.
     * Uses an in-memory sliding window to count how many documents
     * this user deleted in the last {@code bulkDeletionWindowMinutes} minutes.
     * When the count first hits the threshold, all admins receive an alert.
     *
     * @param deletedByUserId numeric id of the user who deleted the document
     * @param userName        display name or email of that user
     * @param documentName    name of the document just deleted
     */
    public void checkAndAlertBulkDeletion(Long deletedByUserId, String userName, String documentName) {
        try {
            Instant now = Instant.now();
            Instant windowStart = now.minusSeconds(bulkDeletionWindowMinutes * 60L);

            Deque<Instant> timestamps = deletionWindows
                    .computeIfAbsent(deletedByUserId, k -> new ArrayDeque<>());

            synchronized (timestamps) {
                // Evict events outside the window
                while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                    timestamps.pollFirst();
                }
                timestamps.addLast(now);
                long count = timestamps.size();

                // Fire alert exactly at threshold (not on every subsequent deletion)
                if (count == bulkDeletionThreshold) {
                    log.warn("BULK_DELETION: user '{}' deleted {} docs in {} minutes",
                            userName, count, bulkDeletionWindowMinutes);

                    String title = "⚠️ Bulk Document Deletion";
                    String message = String.format(
                            "User \"%s\" has deleted %d documents in the last %d minutes. " +
                            "Latest deleted: \"%s\". Please review.",
                            userName, count, bulkDeletionWindowMinutes, documentName);
                    String metadata = String.format(
                            "{\"scope\":\"system\",\"category\":\"document\",\"action\":\"bulk_delete\"," +
                            "\"subject\":\"%s\",\"count\":%d}",
                            escapeJson(userName), count);

                    publishToAllAdmins(NotificationType.ADMIN_BULK_DELETION_ALERT,
                            title, message, "/admin/dashboard", metadata);
                }
            }
        } catch (Exception ex) {
            log.error("Failed to evaluate bulk-deletion alert: {}", ex.getMessage());
        }
    }

    // ─── Storage Warning ─────────────────────────────────────────────────────────

    /**
     * Call this after every successful document upload.
     * Fires a storage warning to all admins when total usage crosses 70 % or 90 %
     * of the configured quota.
     *
     * <p>The alert is idempotent at each threshold: if the system is already above
     * 90 % the 70 % alert is not re-fired; once above 90 % the 90 % alert fires
     * at most once per percentage point to avoid spam.</p>
     */
    public void checkAndAlertStorageUsage() {
        try {
            Long totalBytes = documentRepository.sumTotalStorageBytes();
            if (totalBytes == null || totalBytes == 0 || storageQuotaBytes <= 0) return;

            double usagePct = (totalBytes * 100.0) / storageQuotaBytes;
            log.debug("Storage usage: {:.1f}% ({} / {} bytes)", usagePct, totalBytes, storageQuotaBytes);

            if (usagePct >= 90.0) {
                fireStorageAlert(90, usagePct, totalBytes);
            } else if (usagePct >= 70.0) {
                fireStorageAlert(70, usagePct, totalBytes);
            }
        } catch (Exception ex) {
            log.error("Failed to evaluate storage warning: {}", ex.getMessage());
        }
    }

    private void fireStorageAlert(int thresholdPct, double actualPct, long totalBytes) {
        String usedFormatted = formatBytes(totalBytes);
        String quotaFormatted = formatBytes(storageQuotaBytes);

        String title = thresholdPct >= 90
                ? "🚨 Storage Critical (" + String.format("%.0f%%", actualPct) + " used)"
                : "⚠️ Storage Warning (" + String.format("%.0f%%", actualPct) + " used)";

        String message = String.format(
                "Platform storage is at %.1f%% capacity (%s of %s used). " +
                "Consider cleaning up old documents or increasing the quota.",
                actualPct, usedFormatted, quotaFormatted);

        String metadata = String.format(
                "{\"scope\":\"system\",\"category\":\"storage\",\"action\":\"limit_warning\"," +
                "\"threshold\":%d,\"usagePct\":%.1f,\"usedBytes\":%d,\"quotaBytes\":%d}",
                thresholdPct, actualPct, totalBytes, storageQuotaBytes);

        log.warn("STORAGE_WARNING: {}% threshold crossed. {}", thresholdPct, message);
        publishToAllAdmins(NotificationType.ADMIN_STORAGE_WARNING,
                title, message, "/admin/dashboard", metadata);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────────

    /**
     * Broadcasts an admin-audience notification to every user who has the ADMIN role.
     * Uses the {@link UserRepository} to fetch admin user IDs.
     */
    private void publishToAllAdmins(NotificationType type, String title,
                                     String message, String actionUrl, String metadata) {
        List<Long> adminIds = userRepository.findAllAdminUserIds();
        if (adminIds == null || adminIds.isEmpty()) {
            log.warn("No admin users found to notify for {}", type);
            return;
        }
        for (Long adminId : adminIds) {
            try {
                publisher.publishNotificationEvent(
                        NotificationUserIds.fromUserId(adminId),
                        type, title, message,
                        "system", null,
                        metadata, actionUrl,
                        NotificationAudience.ADMIN
                );
            } catch (Exception ex) {
                log.error("Failed to send {} to admin {}: {}", type, adminId, ex.getMessage());
            }
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1_073_741_824L) return String.format("%.2f GB", bytes / 1_073_741_824.0);
        if (bytes >= 1_048_576L)     return String.format("%.1f MB", bytes / 1_048_576.0);
        if (bytes >= 1_024L)         return String.format("%.0f KB", bytes / 1_024.0);
        return bytes + " B";
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
