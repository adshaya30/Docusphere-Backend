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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin-panel notifications only (audience = ADMIN).
 * Use this from all /admin/* pages — never use {@code /api/notifications} there.
 *
 * <b>Role-Based Behavior:</b>
 * This controller ONLY returns ADMIN-audience notifications for authenticated admin users.
 * It completely isolates admin notifications from user notifications:
 * - All queries filter by {@code NotificationAudience.ADMIN}
 * - Ownership check validates both userId AND audience
 * - User notifications are completely invisible to this endpoint
 * - WebSocket subscriptions use separate queue: {@code /queue/notifications-admin}
 * - Protected by {@code @PreAuthorize("hasRole('ADMIN')")}}
 *
 * <b>Notifications Shown Here:</b>
 * Admin action log - records of administrative actions:
 * - Team creation, deletion, merging
 * - Member management (add, remove, role changes)
 * - Team leader transfers
 * - Document deletion by admin
 *
 * <b>Isolation Strategy:</b>
 * Users with ADMIN role see:
 * - USER notifications in regular app (via /api/notifications)
 * - ADMIN notifications ONLY in admin panel (via /api/admin/notifications)
 * - Never both mixed together
 *
 * <b>Endpoint Summary:</b>
 * - GET /api/admin/notifications - List admin notifications (paginated)
 * - GET /api/admin/notifications/stats - Admin notification statistics
 * - GET /api/admin/notifications/unread-count - Count of unread admin notifications
 * - GET /api/admin/notifications/by-status/{status} - Notifications filtered by status
 * - GET /api/admin/notifications/{notificationId} - Get single admin notification
 * - PUT /api/admin/notifications/{notificationId}/read - Mark as read
 * - PUT /api/admin/notifications/mark-all-read - Mark all admin notifications as read
 * - DELETE /api/admin/notifications/{notificationId} - Delete admin notification
 *
 * Use this endpoint ONLY from admin pages (/admin/*). For regular app, use {@link NotificationController}.
 */
@RestController
@RequestMapping("/api/admin/notifications")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Notifications", description = "Admin action log and platform notifications for the admin panel")
@Slf4j
public class AdminNotificationController {

    private static final NotificationAudience AUDIENCE = NotificationAudience.ADMIN;

    private final NotificationService notificationService;
    private final NotificationAuthSupport authSupport;

    public AdminNotificationController(NotificationService notificationService,
                                         NotificationAuthSupport authSupport) {
        this.notificationService = notificationService;
        this.authSupport = authSupport;
    }

    private boolean owns(NotificationDTO n, UUID userId) {
        // Must verify both userId AND audience to prevent role-based notification mixing
        boolean owns = n.getUserId().equals(userId);
        boolean isAdminAudience = n.getAudience() == AUDIENCE;
        
        if (!isAdminAudience) {
            log.warn("Attempted access to non-admin notification: {} with audience: {} for admin: {}",
                n.getId(), n.getAudience(), userId);
            return false;
        }
        
        return owns;
    }

    @GetMapping
    @Operation(summary = "List admin notifications",
        description = "Admin action log only — team management, merges, document deletes performed by this admin.")
    public ResponseEntity<NotificationPageDTO> list(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        UUID userId = authSupport.extractUserId(request);
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, AUDIENCE, page, pageSize));
    }

    @GetMapping("/stats")
    public ResponseEntity<NotificationStatsDTO> stats(HttpServletRequest request) {
        UUID userId = authSupport.extractUserId(request);
        return ResponseEntity.ok(notificationService.getNotificationStats(userId, AUDIENCE));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> unreadCount(HttpServletRequest request) {
        UUID userId = authSupport.extractUserId(request);
        return ResponseEntity.ok(notificationService.getUnreadCount(userId, AUDIENCE));
    }

    @GetMapping("/by-status/{status}")
    public ResponseEntity<NotificationPageDTO> byStatus(
            HttpServletRequest request,
            @PathVariable NotificationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        UUID userId = authSupport.extractUserId(request);
        return ResponseEntity.ok(
            notificationService.getUserNotificationsByStatus(userId, AUDIENCE, status, page, pageSize));
    }

    @GetMapping("/{notificationId}")
    public ResponseEntity<NotificationDTO> getOne(
            HttpServletRequest request, @PathVariable UUID notificationId) {
        UUID userId = authSupport.extractUserId(request);
        NotificationDTO n = notificationService.getNotificationById(notificationId);
        if (!owns(n, userId)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(n);
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(HttpServletRequest request, @PathVariable UUID notificationId) {
        UUID userId = authSupport.extractUserId(request);
        NotificationDTO n = notificationService.getNotificationById(notificationId);
        if (!owns(n, userId)) {
            return ResponseEntity.status(403).build();
        }
        notificationService.markAsRead(notificationId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{notificationId}/unread")
    public ResponseEntity<Void> markUnread(HttpServletRequest request, @PathVariable UUID notificationId) {
        UUID userId = authSupport.extractUserId(request);
        NotificationDTO n = notificationService.getNotificationById(notificationId);
        if (!owns(n, userId)) {
            return ResponseEntity.status(403).build();
        }
        notificationService.markAsUnread(notificationId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/mark-all-read")
    public ResponseEntity<Void> markAllRead(HttpServletRequest request) {
        UUID userId = authSupport.extractUserId(request);
        notificationService.markAllAsRead(userId, AUDIENCE);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> delete(HttpServletRequest request, @PathVariable UUID notificationId) {
        UUID userId = authSupport.extractUserId(request);
        NotificationDTO n = notificationService.getNotificationById(notificationId);
        if (!owns(n, userId)) {
            return ResponseEntity.status(403).build();
        }
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/clear-all")
    @Operation(summary = "Clear all admin notifications",
        description = "Permanently deletes all ADMIN-audience notifications (action log) for the authenticated admin.")
    public ResponseEntity<Void> clearAllNotifications(HttpServletRequest request) {
        UUID userId = authSupport.extractUserId(request);
        notificationService.clearAllNotifications(userId, AUDIENCE);
        return ResponseEntity.noContent().build();
    }
}
