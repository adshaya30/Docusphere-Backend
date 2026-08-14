package com.docusphere.backend.notification.service;

import com.docusphere.backend.notification.dto.NotificationDTO;
import com.docusphere.backend.notification.dto.NotificationPageDTO;
import com.docusphere.backend.notification.dto.NotificationStatsDTO;
import com.docusphere.backend.notification.dto.WebSocketNotificationDTO;
import com.docusphere.backend.notification.entity.Notification;
import com.docusphere.backend.notification.entity.NotificationAudience;
import com.docusphere.backend.notification.entity.NotificationStatus;
import com.docusphere.backend.notification.entity.NotificationType;
import com.docusphere.backend.notification.repository.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for managing notifications with role-based audience separation.
 *
 * <b>Role-Based Separation:</b>
 * - USER audience: Regular user notifications (team invites, document shares, member changes)
 * - ADMIN audience: Admin-panel only notifications (admin action log)
 *
 * <b>Critical Design Rules:</b>
 * 1. ALWAYS filter by audience when querying notifications
 * 2. Admin notification types (ADMIN_*) are automatically routed to ADMIN audience
 * 3. Never mix USER and ADMIN audiences in the same query or response
 * 4. Controllers enforce role-based context (user space vs admin space)
 * 5. WebSocket subscriptions are audience-specific
 *
 * <b>Methods MUST specify audience:</b>
 * - Use: {@code getUserNotifications(userId, NotificationAudience.USER, ...)}
 * - Use: {@code getUserNotifications(userId, NotificationAudience.ADMIN, ...)}
 * - NEVER: {@code findByUserId(...)} without audience (doesn't exist for this reason)
 *
 * Handles creation, retrieval, status updates, and real-time distribution of notifications.
 */
@Service
@Slf4j
@Transactional
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    public NotificationService(NotificationRepository notificationRepository,
                               SimpMessagingTemplate messagingTemplate,
                               ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new notification and send it in real-time via WebSocket
     */
    public NotificationDTO createNotification(UUID userId, NotificationType type, String title,
                                             String message, UUID relatedEntityId, String relatedEntityType) {
        return createNotification(userId, type, NotificationAudience.USER, title, message,
            relatedEntityId, relatedEntityType, null, null);
    }

    /**
     * Create a new notification with optional metadata and action URL
     */
    public NotificationDTO createNotification(UUID userId, NotificationType type, String title,
                                             String message, UUID relatedEntityId, String relatedEntityType,
                                             String metadata, String actionUrl) {
        return createNotification(userId, type, NotificationAudience.USER, title, message,
            relatedEntityId, relatedEntityType, metadata, actionUrl);
    }

    public NotificationDTO createNotification(UUID userId, NotificationType type, NotificationAudience audience,
                                             String title, String message, UUID relatedEntityId,
                                             String relatedEntityType, String metadata, String actionUrl) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setAudience(NotificationAudience.resolve(type, audience));
        notification.setTitle(title != null ? title : type.getTitle());
        notification.setMessage(message != null ? message : type.getDefaultMessage());
        notification.setRelatedEntityId(relatedEntityId);
        notification.setRelatedEntityType(relatedEntityType);
        notification.setMetadata(metadata);
        notification.setActionUrl(actionUrl);
        notification.setStatus(NotificationStatus.UNREAD);

        Notification saved = notificationRepository.save(notification);
        log.info("Notification created: {} for user: {}", saved.getId(), userId);

        // Send real-time notification via WebSocket
        sendRealtimeNotification(saved);

        return convertToDTO(saved);
    }

    /**
     * Send notification in real-time via WebSocket
     */
    public void sendRealtimeNotification(Notification notification) {
        try {
            WebSocketNotificationDTO wsDto = convertToWebSocketDTO(notification);
            String queue = notification.getAudience().websocketQueue();
            messagingTemplate.convertAndSendToUser(
                notification.getUserId().toString(),
                queue,
                wsDto
            );
            log.debug("Real-time notification sent to user: {} on {}", notification.getUserId(), queue);
        } catch (Exception e) {
            log.error("Error sending real-time notification: ", e);
        }
    }

    /**
     * Get all notifications for a user, paginated
     */
    @Transactional(readOnly = true)
    public NotificationPageDTO getUserNotifications(UUID userId, NotificationAudience audience, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<Notification> notificationsPage = notificationRepository
            .findByUserIdAndAudienceOrderByCreatedAtDesc(userId, audience, pageable);

        return NotificationPageDTO.builder()
            .content(notificationsPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()))
            .pageNumber(notificationsPage.getNumber())
            .pageSize(notificationsPage.getSize())
            .totalElements(notificationsPage.getTotalElements())
            .totalPages(notificationsPage.getTotalPages())
            .hasNext(notificationsPage.hasNext())
            .hasPrevious(notificationsPage.hasPrevious())
            .build();
    }

    /**
     * Get unread notifications for a user by audience type.
     * IMPORTANT: Must specify audience to prevent mixing admin and user notifications.
     * 
     * @param userId the user ID
     * @param audience the notification audience (USER or ADMIN)
     * @return list of unread notifications for the specified audience
     */
    @Transactional(readOnly = true)
    public List<NotificationDTO> getUnreadNotifications(UUID userId, NotificationAudience audience) {
        return notificationRepository.findByUserIdAndAudienceAndStatusOrderByCreatedAtDesc(
                userId, audience, NotificationStatus.UNREAD, PageRequest.of(0, Integer.MAX_VALUE))
            .getContent()
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get notifications by status for a user, paginated
     */
    @Transactional(readOnly = true)
    public NotificationPageDTO getUserNotificationsByStatus(UUID userId, NotificationAudience audience,
                                                            NotificationStatus status, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<Notification> notificationsPage = notificationRepository
            .findByUserIdAndAudienceAndStatusOrderByCreatedAtDesc(userId, audience, status, pageable);

        return NotificationPageDTO.builder()
            .content(notificationsPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()))
            .pageNumber(notificationsPage.getNumber())
            .pageSize(notificationsPage.getSize())
            .totalElements(notificationsPage.getTotalElements())
            .totalPages(notificationsPage.getTotalPages())
            .hasNext(notificationsPage.hasNext())
            .hasPrevious(notificationsPage.hasPrevious())
            .build();
    }

    /**
     * Get notifications by type for a user, paginated
     */
    @Transactional(readOnly = true)
    public NotificationPageDTO getUserNotificationsByType(UUID userId, NotificationType type, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize);
        Page<Notification> notificationsPage = notificationRepository
            .findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, pageable);

        return NotificationPageDTO.builder()
            .content(notificationsPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()))
            .pageNumber(notificationsPage.getNumber())
            .pageSize(notificationsPage.getSize())
            .totalElements(notificationsPage.getTotalElements())
            .totalPages(notificationsPage.getTotalPages())
            .hasNext(notificationsPage.hasNext())
            .hasPrevious(notificationsPage.hasPrevious())
            .build();
    }

    /**
     * Get a single notification by ID
     */
    @Transactional(readOnly = true)
    public NotificationDTO getNotificationById(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));
        return convertToDTO(notification);
    }

    /**
     * Mark a notification as read
     */
    public void markAsRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));
        notification.markAsRead();
        notificationRepository.save(notification);
        log.info("Notification marked as read: {}", notificationId);
    }

    /**
     * Mark a notification as unread
     */
    public void markAsUnread(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));
        notification.markAsUnread();
        notificationRepository.save(notification);
        log.info("Notification marked as unread: {}", notificationId);
    }

    /**
     * Mark all unread notifications as read for a user
     */
    public void markAllAsRead(UUID userId, NotificationAudience audience) {
        notificationRepository.markAllAsRead(userId, audience, LocalDateTime.now());
        log.info("All notifications marked as read for user: {}, audience: {}", userId, audience);
    }

    /**
     * Archive a notification
     */
    public void archiveNotification(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new RuntimeException("Notification not found: " + notificationId));
        notification.archive();
        notificationRepository.save(notification);
        log.info("Notification archived: {}", notificationId);
    }

    /**
     * Delete a notification
     */
    public void deleteNotification(UUID notificationId) {
        notificationRepository.deleteById(notificationId);
        log.info("Notification deleted: {}", notificationId);
    }

    /**
     * Delete all notifications for a user by audience (clear-all feature).
     * USER audience: clears all user notifications.
     * ADMIN audience: clears all admin action-log notifications.
     */
    public void clearAllNotifications(UUID userId, NotificationAudience audience) {
        notificationRepository.deleteByUserIdAndAudience(userId, audience);
        log.info("All {} notifications cleared for user: {}", audience, userId);
    }

    /**
     * Get notification statistics for a user
     */
    @Transactional(readOnly = true)
    public NotificationStatsDTO getNotificationStats(UUID userId, NotificationAudience audience) {
        long unreadCount = notificationRepository.countByUserIdAndAudienceAndStatus(
            userId, audience, NotificationStatus.UNREAD);
        long readCount = notificationRepository.countByUserIdAndAudienceAndStatus(
            userId, audience, NotificationStatus.READ);
        long totalCount = notificationRepository.countByUserIdAndAudience(userId, audience);

        return NotificationStatsDTO.builder()
            .unreadCount(unreadCount)
            .readCount(readCount)
            .totalCount(totalCount)
            .build();
    }

    /**
     * Get count of unread notifications for a user
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId, NotificationAudience audience) {
        return notificationRepository.countByUserIdAndAudienceAndStatus(
            userId, audience, NotificationStatus.UNREAD);
    }

    /**
     * Count all notifications for a user (any audience) - for debug purposes
     */
    @Transactional(readOnly = true)
    public long countAllForUser(UUID userId) {
        return notificationRepository.countByUserId(userId);
    }

    /**
     * Get recent notifications for a user (created in last N minutes)
     */
    @Transactional(readOnly = true)
    public List<NotificationDTO> getRecentNotifications(UUID userId, int minutesBack) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutesBack);
        return notificationRepository.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, since)
            .stream()
            .map(this::convertToDTO)
            .collect(Collectors.toList());
    }

    /**
     * Delete old archived notifications
     */
    public void deleteOldArchivedNotifications(int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        notificationRepository.deleteByStatusAndArchivedAtBefore(NotificationStatus.ARCHIVED, cutoffDate);
        log.info("Deleted archived notifications older than {} days", daysOld);
    }

    /**
     * Delete all old notifications
     */
    public void deleteOldNotifications(int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        notificationRepository.deleteByCreatedAtBefore(cutoffDate);
        log.info("Deleted all notifications older than {} days", daysOld);
    }

    /**
     * Convert Notification entity to DTO
     */
    private NotificationDTO convertToDTO(Notification notification) {
        return NotificationDTO.builder()
            .id(notification.getId())
            .userId(notification.getUserId())
            .type(notification.getType())
            .audience(notification.getAudience())
            .title(notification.getTitle())
            .message(notification.getMessage())
            .metadata(notification.getMetadata())
            .status(notification.getStatus())
            .relatedEntityId(notification.getRelatedEntityId())
            .relatedEntityType(notification.getRelatedEntityType())
            .actionUrl(notification.getActionUrl())
            .createdAt(notification.getCreatedAt())
            .readAt(notification.getReadAt())
            .archivedAt(notification.getArchivedAt())
            .build();
    }

    /**
     * Convert Notification entity to WebSocket DTO
     */
    private WebSocketNotificationDTO convertToWebSocketDTO(Notification notification) {
        return WebSocketNotificationDTO.builder()
            .id(notification.getId().toString())
            .audience(notification.getAudience().name())
            .type(notification.getType().toString())
            .title(notification.getTitle())
            .message(notification.getMessage())
            .status(notification.getStatus().toString())
            .relatedEntityId(notification.getRelatedEntityId() != null ? notification.getRelatedEntityId().toString() : null)
            .relatedEntityType(notification.getRelatedEntityType())
            .actionUrl(notification.getActionUrl())
            .createdAt(notification.getCreatedAt().format(DATE_FORMATTER))
            .metadata(notification.getMetadata())
            .unread(notification.isUnread())
            .build();
    }
}
