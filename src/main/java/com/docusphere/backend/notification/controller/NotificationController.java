package com.docusphere.backend.notification.controller;

import com.docusphere.backend.notification.dto.NotificationDTO;
import com.docusphere.backend.notification.dto.NotificationPageDTO;
import com.docusphere.backend.notification.dto.NotificationStatsDTO;
import com.docusphere.backend.notification.entity.NotificationAudience;
import com.docusphere.backend.notification.entity.NotificationStatus;
import com.docusphere.backend.notification.service.NotificationService;
import com.docusphere.backend.notification.support.NotificationAuthSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * User-mode notifications only (team invites, document shares, membership changes).
 * Admin panel must use {@link AdminNotificationController} at {@code /api/admin/notifications}.
 *
 * <b>Role-Based Behavior:</b>
 * This controller ONLY returns USER-audience notifications. It explicitly rejects:
 * - Requests from admin context (checked via context parameter)
 * - Admin notification types (audience auto-forced to ADMIN)
 * - Attempts to access admin notifications
 *
 * <b>Isolation Strategy:</b>
 * - All queries filter by {@code NotificationAudience.USER}
 * - Ownership check validates both userId AND audience
 * - Admin notifications are completely invisible to this endpoint
 * - WebSocket subscriptions use separate queue: {@code /queue/notifications-user}
 *
 * <b>Endpoint Summary:</b>
 * - GET /api/notifications - List user notifications (paginated)
 * - GET /api/notifications/stats - User notification statistics
 * - GET /api/notifications/unread-count - Count of unread user notifications
 * - GET /api/notifications/by-status/{status} - Notifications filtered by status
 * - GET /api/notifications/{notificationId} - Get single user notification
 * - PUT /api/notifications/{notificationId}/read - Mark as read
 *
 * Use this endpoint from all non-admin pages. For admin pages, use {@link AdminNotificationController}.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "User notifications for the regular app (not admin panel)")
@Slf4j
public class NotificationController {

    private static final NotificationAudience AUDIENCE = NotificationAudience.USER;

    private final NotificationService notificationService;
    private final NotificationAuthSupport authSupport;

    public NotificationController(NotificationService notificationService,
                                  NotificationAuthSupport authSupport) {
        this.notificationService = notificationService;
        this.authSupport = authSupport;
    }

    private void rejectAdminContext(String context) {
        if ("admin".equalsIgnoreCase(context)) {
            throw new IllegalArgumentException(
                "Use /api/admin/notifications on admin pages. This endpoint returns user notifications only.");
        }
    }

    /**
     * Validates that a notification belongs to the user and is a USER-audience notification.
     * This ensures admin notifications never leak into the user endpoint.
     */
    private boolean owns(NotificationDTO n, UUID userId) {
        // Must verify both userId AND audience to prevent role-based notification mixing
        boolean owns = n.getUserId().equals(userId);
        boolean isUserAudience = n.getAudience() == AUDIENCE;
        
        if (!isUserAudience) {
            log.warn("Attempted access to non-user notification: {} with audience: {} for user: {}",
                n.getId(), n.getAudience(), userId);
            return false;
        }
        
        return owns;
    }

    @GetMapping
    @Operation(summary = "Get user notifications",
        description = "Team invites, document shares, and membership updates. Not for /admin/* pages.")
    public ResponseEntity<NotificationPageDTO> getNotifications(
            HttpServletRequest request,
            @RequestParam(required = false) String context,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        rejectAdminContext(context);
        UUID userId = authSupport.extractUserId(request);
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, AUDIENCE, page, pageSize));
    }

    @GetMapping("/stats")
    public ResponseEntity<NotificationStatsDTO> getNotificationStats(
            HttpServletRequest request, @RequestParam(required = false) String context) {
        rejectAdminContext(context);
        UUID userId = authSupport.extractUserId(request);
        return ResponseEntity.ok(notificationService.getNotificationStats(userId, AUDIENCE));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(
            HttpServletRequest request, @RequestParam(required = false) String context) {
        rejectAdminContext(context);
        UUID userId = authSupport.extractUserId(request);
        return ResponseEntity.ok(notificationService.getUnreadCount(userId, AUDIENCE));
    }

    @GetMapping("/debug-me")
    public ResponseEntity<java.util.Map<String, Object>> debugMe(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        Long rawId = null;
        UUID derivedUuid = null;
        try {
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                rawId = authSupport.getRawUserId(authHeader.substring(7));
                derivedUuid = authSupport.extractUserId(request);
            }
        } catch (Exception e) {
            log.error("Debug error", e);
        }
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("rawLongUserId", rawId);
        result.put("derivedNotificationUUID", derivedUuid != null ? derivedUuid.toString() : null);
        result.put("totalNotificationsForUUID", derivedUuid != null ? notificationService.countAllForUser(derivedUuid) : -1);
        result.put("userNotifications", derivedUuid != null ? notificationService.getUserNotifications(derivedUuid, AUDIENCE, 0, 5) : null);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/by-status/{status}")
    public ResponseEntity<NotificationPageDTO> getNotificationsByStatus(
            HttpServletRequest request,
            @PathVariable NotificationStatus status,
            @RequestParam(required = false) String context,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        rejectAdminContext(context);
        UUID userId = authSupport.extractUserId(request);
        return ResponseEntity.ok(
            notificationService.getUserNotificationsByStatus(userId, AUDIENCE, status, page, pageSize));
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationDTO> getNotification(
            HttpServletRequest request,
            @PathVariable UUID notificationId,
            @RequestParam(required = false) String context) {
        rejectAdminContext(context);
        UUID userId = authSupport.extractUserId(request);
        NotificationDTO notification = notificationService.getNotificationById(notificationId);
        if (!owns(notification, userId)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(notification);
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            HttpServletRequest request,
            @PathVariable UUID notificationId,
            @RequestParam(required = false) String context) {
        rejectAdminContext(context);
        UUID userId = authSupport.extractUserId(request);
        NotificationDTO notification = notificationService.getNotificationById(notificationId);
        if (!owns(notification, userId)) {
            return ResponseEntity.status(403).build();
        }
        notificationService.markAsRead(notificationId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{notificationId}/unread")
    public ResponseEntity<Void> markAsUnread(
            HttpServletRequest request,
            @PathVariable UUID notificationId,
            @RequestParam(required = false) String context) {
        rejectAdminContext(context);
        UUID userId = authSupport.extractUserId(request);
        NotificationDTO notification = notificationService.getNotificationById(notificationId);
        if (!owns(notification, userId)) {
            return ResponseEntity.status(403).build();
        }
        notificationService.markAsUnread(notificationId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<Void> markAllAsRead(
            HttpServletRequest request, @RequestParam(required = false) String context) {
        rejectAdminContext(context);
        UUID userId = authSupport.extractUserId(request);
        notificationService.markAllAsRead(userId, AUDIENCE);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{notificationId}/archive")
    public ResponseEntity<Void> archiveNotification(
            HttpServletRequest request,
            @PathVariable UUID notificationId,
            @RequestParam(required = false) String context) {
        rejectAdminContext(context);
        UUID userId = authSupport.extractUserId(request);
        NotificationDTO notification = notificationService.getNotificationById(notificationId);
        if (!owns(notification, userId)) {
            return ResponseEntity.status(403).build();
        }
        notificationService.archiveNotification(notificationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            HttpServletRequest request,
            @PathVariable UUID notificationId,
            @RequestParam(required = false) String context) {
        rejectAdminContext(context);
        UUID userId = authSupport.extractUserId(request);
        NotificationDTO notification = notificationService.getNotificationById(notificationId);
        if (!owns(notification, userId)) {
            return ResponseEntity.status(403).build();
        }
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/clear-all")
    @Operation(summary = "Clear all user notifications",
        description = "Permanently deletes all USER-audience notifications for the authenticated user.")
    public ResponseEntity<Void> clearAllNotifications(
            HttpServletRequest request,
            @RequestParam(required = false) String context) {
        rejectAdminContext(context);
        UUID userId = authSupport.extractUserId(request);
        notificationService.clearAllNotifications(userId, AUDIENCE);
        return ResponseEntity.noContent().build();
    }
}
