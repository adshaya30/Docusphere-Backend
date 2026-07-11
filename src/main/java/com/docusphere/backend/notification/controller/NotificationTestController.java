package com.docusphere.backend.notification.controller;

import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.notification.dto.NotificationDTO;
import com.docusphere.backend.notification.entity.NotificationAudience;
import com.docusphere.backend.notification.entity.NotificationType;
import com.docusphere.backend.notification.util.NotificationUserIds;
import com.docusphere.backend.notification.service.NotificationHelper;
import com.docusphere.backend.notification.service.AdminNotificationHelper;
import com.docusphere.backend.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Test controller for notification system
 * IMPORTANT: Separate endpoints for USER and ADMIN notifications to prevent mixing during testing
 * 
 * USER Notification Test Endpoints (/api/notifications/test/user/*)
 * - Only creates USER-audience notifications
 * - Use from regular app context
 * 
 * ADMIN Notification Test Endpoints (/api/notifications/test/admin/*)
 * - Only creates ADMIN-audience notifications
 * - Requires ADMIN role
 * - Use from admin page context
 * 
 * These endpoints are for testing/development only
 */
@RestController
@RequestMapping("/api/notifications/test")
@Tag(name = "Notifications - Test", description = "Test endpoints for notification system (user and admin)")
@Slf4j
public class NotificationTestController {

    private final NotificationService notificationService;
    private final NotificationHelper notificationHelper;
    private final AdminNotificationHelper adminNotificationHelper;
    private final JwtService jwtService;

    public NotificationTestController(NotificationService notificationService,
                                      NotificationHelper notificationHelper,
                                      AdminNotificationHelper adminNotificationHelper,
                                      JwtService jwtService) {
        this.notificationService = notificationService;
        this.notificationHelper = notificationHelper;
        this.adminNotificationHelper = adminNotificationHelper;
        this.jwtService = jwtService;
    }

    /**
     * Extract userId from JWT token in Authorization header
     */
    private UUID extractUserIdFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Long userId = jwtService.extractUserId(token);
            return NotificationUserIds.fromUserId(userId);
        }
        throw new IllegalArgumentException("Invalid or missing token");
    }

    private Long extractUserIdLongFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtService.extractUserId(token);
        }
        throw new IllegalArgumentException("Invalid or missing token");
    }

    // ========================================================================
    // USER NOTIFICATION TEST ENDPOINTS - For /api/notifications testing only
    // These create USER-audience notifications. Do NOT use in admin context.
    // ========================================================================

    /**
     * Send a test notification to current user (USER audience)
     */
    @PostMapping("/user/send-test")
    @Operation(summary = "Send test USER notification", 
        description = "Send a test USER-audience notification to yourself. Use from regular app only.")
    public ResponseEntity<NotificationDTO> sendTestNotification(HttpServletRequest request) {
        UUID userId = extractUserIdFromToken(request);
        
        NotificationDTO notification = notificationService.createNotification(
            userId,
            NotificationType.SYSTEM_NOTIFICATION,
            "Test Notification",
            "This is a test USER notification from Docusphere! If you see this, everything is working correctly.",
            null,
            "system",
            null,
            null
        );
        
        log.info("Test USER notification sent to user: {}", userId);
        return ResponseEntity.ok(notification);
    }

    /**
     * Send a document shared notification (USER audience)
     */
    @PostMapping("/user/send-document-shared")
    @Operation(summary = "Send document shared USER notification")
    public ResponseEntity<?> sendDocumentSharedNotification(HttpServletRequest request) {
        UUID userId = extractUserIdFromToken(request);
        UUID testDocumentId = UUID.randomUUID();
        
        notificationHelper.notifyDocumentShared(
            userId,
            testDocumentId,
            "Test Document - Project Report.pdf",
            "John Doe"
        );
        
        log.info("Document shared USER notification sent to user: {}", userId);
        return ResponseEntity.ok("Document shared USER notification sent");
    }

    /**
     * Send a team invitation notification (USER audience)
     */
    @PostMapping("/user/send-team-invitation")
    @Operation(summary = "Send team invitation USER notification")
    public ResponseEntity<?> sendTeamInvitationNotification(HttpServletRequest request) {
        UUID userId = extractUserIdFromToken(request);
        UUID testTeamId = UUID.randomUUID();
        
        notificationHelper.notifyTeamInvitation(
            userId,
            testTeamId,
            "Engineering Team",
            null,
            null
        );
        
        log.info("Team invitation USER notification sent to user: {}", userId);
        return ResponseEntity.ok("Team invitation USER notification sent");
    }

    /**
     * Send an action assigned notification (USER audience)
     */
    @PostMapping("/user/send-action-assigned")
    @Operation(summary = "Send action assigned USER notification")
    public ResponseEntity<?> sendActionAssignedNotification(HttpServletRequest request) {
        UUID userId = extractUserIdFromToken(request);
        UUID testActionId = UUID.randomUUID();
        
        notificationHelper.notifyActionAssigned(
            userId,
            testActionId,
            "Review Project Report",
            "Quarterly Business Review.pdf"
        );
        
        log.info("Action assigned USER notification sent to user: {}", userId);
        return ResponseEntity.ok("Action assigned USER notification sent");
    }

    /**
     * Send a user added to team notification (USER audience)
     */
    @PostMapping("/user/send-user-added-to-team")
    @Operation(summary = "Send user added to team notification")
    public ResponseEntity<?> sendUserAddedToTeamNotification(HttpServletRequest request) {
        UUID userId = extractUserIdFromToken(request);
        UUID testTeamId = UUID.randomUUID();
        
        notificationHelper.notifyUserAddedToTeam(
            userId,
            testTeamId,
            "Engineering Team"
        );
        
        log.info("User added to team USER notification sent to user: {}", userId);
        return ResponseEntity.ok("User added to team USER notification sent");
    }

    /**
     * Send a user removed from team notification (USER audience)
     */
    @PostMapping("/user/send-user-removed-from-team")
    @Operation(summary = "Send user removed from team notification")
    public ResponseEntity<?> sendUserRemovedFromTeamNotification(HttpServletRequest request) {
        UUID userId = extractUserIdFromToken(request);
        UUID testTeamId = UUID.randomUUID();
        
        notificationHelper.notifyUserRemovedFromTeam(
            userId,
            testTeamId,
            "Engineering Team"
        );
        
        log.info("User removed from team USER notification sent to user: {}", userId);
        return ResponseEntity.ok("User removed from team USER notification sent");
    }

    /**
     * Clear all USER notifications for current user
     */
    @DeleteMapping("/user/clear-all")
    @Operation(summary = "Clear all USER notifications", 
        description = "Delete all USER-audience notifications for current user (TEST ONLY)")
    public ResponseEntity<?> clearAllUserNotifications(HttpServletRequest request) {
        UUID userId = extractUserIdFromToken(request);
        
        var page = notificationService.getUserNotifications(userId, NotificationAudience.USER, 0, Integer.MAX_VALUE);
        page.getContent().forEach(n -> notificationService.deleteNotification(n.getId()));
        
        log.warn("All USER notifications cleared for user: {}", userId);
        return ResponseEntity.ok("All USER notifications cleared");
    }

    // ========================================================================
    // ADMIN NOTIFICATION TEST ENDPOINTS - For /api/admin/notifications testing only
    // These create ADMIN-audience notifications. Only use in admin context.
    // ========================================================================

    /**
     * Send a test ADMIN notification (ADMIN audience - requires ADMIN role)
     */
    @PostMapping("/admin/send-test")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send test ADMIN notification", 
        description = "Send a test ADMIN-audience notification. Requires ADMIN role. Use from admin pages only.")
    public ResponseEntity<?> sendAdminTestNotification(HttpServletRequest request) {
        Long adminUserId = extractUserIdLongFromToken(request);
        UUID testTeamId = UUID.randomUUID();
        
        adminNotificationHelper.notifyTeamCreated(
            adminUserId,
            testTeamId,
            "Test Team"
        );
        
        log.info("Test ADMIN notification sent to admin: {}", adminUserId);
        return ResponseEntity.ok("Test ADMIN notification sent");
    }

    /**
     * Send a team created ADMIN notification (ADMIN audience - requires ADMIN role)
     */
    @PostMapping("/admin/send-team-created")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send team created ADMIN notification")
    public ResponseEntity<?> sendAdminTeamCreated(HttpServletRequest request) {
        Long adminUserId = extractUserIdLongFromToken(request);
        UUID testTeamId = UUID.randomUUID();
        
        adminNotificationHelper.notifyTeamCreated(
            adminUserId,
            testTeamId,
            "Test Team Created"
        );
        
        log.info("Team created ADMIN notification sent to admin: {}", adminUserId);
        return ResponseEntity.ok("Team created ADMIN notification sent");
    }

    /**
     * Send a team deleted ADMIN notification (ADMIN audience - requires ADMIN role)
     */
    @PostMapping("/admin/send-team-deleted")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send team deleted ADMIN notification")
    public ResponseEntity<?> sendAdminTeamDeleted(HttpServletRequest request) {
        Long adminUserId = extractUserIdLongFromToken(request);
        UUID testTeamId = UUID.randomUUID();
        
        adminNotificationHelper.notifyTeamDeleted(
            adminUserId,
            testTeamId,
            "Test Team Deleted"
        );
        
        log.info("Team deleted ADMIN notification sent to admin: {}", adminUserId);
        return ResponseEntity.ok("Team deleted ADMIN notification sent");
    }

    /**
     * Send a member added ADMIN notification (ADMIN audience - requires ADMIN role)
     */
    @PostMapping("/admin/send-member-added")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send member added ADMIN notification")
    public ResponseEntity<?> sendAdminMemberAdded(HttpServletRequest request) {
        Long adminUserId = extractUserIdLongFromToken(request);
        UUID testTeamId = UUID.randomUUID();
        
        adminNotificationHelper.notifyMemberAdded(
            adminUserId,
            testTeamId,
            "Test Team",
            "Test Member"
        );
        
        log.info("Member added ADMIN notification sent to admin: {}", adminUserId);
        return ResponseEntity.ok("Member added ADMIN notification sent");
    }

    /**
     * Send a member removed ADMIN notification (ADMIN audience - requires ADMIN role)
     */
    @PostMapping("/admin/send-member-removed")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send member removed ADMIN notification")
    public ResponseEntity<?> sendAdminMemberRemoved(HttpServletRequest request) {
        Long adminUserId = extractUserIdLongFromToken(request);
        UUID testTeamId = UUID.randomUUID();
        
        adminNotificationHelper.notifyMemberRemoved(
            adminUserId,
            testTeamId,
            "Test Team",
            "Test Member"
        );
        
        log.info("Member removed ADMIN notification sent to admin: {}", adminUserId);
        return ResponseEntity.ok("Member removed ADMIN notification sent");
    }

    /**
     * Send a document deleted ADMIN notification (ADMIN audience - requires ADMIN role)
     */
    @PostMapping("/admin/send-document-deleted")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send document deleted ADMIN notification")
    public ResponseEntity<?> sendAdminDocumentDeleted(HttpServletRequest request) {
        Long adminUserId = extractUserIdLongFromToken(request);
        UUID testDocId = UUID.randomUUID();
        
        adminNotificationHelper.notifyDocumentDeleted(
            adminUserId,
            testDocId,
            "Test Document.pdf",
            "Test Team"
        );
        
        log.info("Document deleted ADMIN notification sent to admin: {}", adminUserId);
        return ResponseEntity.ok("Document deleted ADMIN notification sent");
    }

    /**
     * Send a user registered ADMIN notification (ADMIN audience - requires ADMIN role)
     */
    @PostMapping("/admin/send-user-registered")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send user registered ADMIN notification")
    public ResponseEntity<?> sendAdminUserRegistered(HttpServletRequest request) {
        Long adminUserId = extractUserIdLongFromToken(request);
        UUID testUserId = UUID.randomUUID();
        
        adminNotificationHelper.notifyUserRegistered(
            adminUserId,
            testUserId,
            "newuser@example.com",
            "New Test User"
        );
        
        log.info("User registered ADMIN notification sent to admin: {}", adminUserId);
        return ResponseEntity.ok("User registered ADMIN notification sent");
    }

    /**
     * Send a user account deleted ADMIN notification (ADMIN audience - requires ADMIN role)
     */
    @PostMapping("/admin/send-user-account-deleted")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send user account deleted ADMIN notification")
    public ResponseEntity<?> sendAdminUserAccountDeleted(HttpServletRequest request) {
        Long adminUserId = extractUserIdLongFromToken(request);
        UUID testUserId = UUID.randomUUID();
        
        adminNotificationHelper.notifyUserAccountDeleted(
            adminUserId,
            testUserId,
            "Deleted Test User",
            "deleted@example.com"
        );
        
        log.info("User account deleted ADMIN notification sent to admin: {}", adminUserId);
        return ResponseEntity.ok("User account deleted ADMIN notification sent");
    }

    /**
     * Send a user role changed ADMIN notification (ADMIN audience - requires ADMIN role)
     */
    @PostMapping("/admin/send-user-role-changed")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send user role changed ADMIN notification")
    public ResponseEntity<?> sendAdminUserRoleChanged(HttpServletRequest request) {
        Long adminUserId = extractUserIdLongFromToken(request);
        UUID testUserId = UUID.randomUUID();
        
        adminNotificationHelper.notifyUserRoleChanged(
            adminUserId,
            testUserId,
            "Test User",
            "TEAM_LEAD",
            "Engineering Team"
        );
        
        log.info("User role changed ADMIN notification sent to admin: {}", adminUserId);
        return ResponseEntity.ok("User role changed ADMIN notification sent");
    }

    /**
     * Clear all ADMIN notifications for current admin user
     */
    @DeleteMapping("/admin/clear-all")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Clear all ADMIN notifications", 
        description = "Delete all ADMIN-audience notifications for current admin (TEST ONLY)")
    public ResponseEntity<?> clearAllAdminNotifications(HttpServletRequest request) {
        UUID adminUserId = extractUserIdFromToken(request);
        
        var page = notificationService.getUserNotifications(adminUserId, NotificationAudience.ADMIN, 0, Integer.MAX_VALUE);
        page.getContent().forEach(n -> notificationService.deleteNotification(n.getId()));
        
        log.warn("All ADMIN notifications cleared for admin: {}", adminUserId);
        return ResponseEntity.ok("All ADMIN notifications cleared");
    }

    /**
     * Get database info about USER notifications
     */
    @GetMapping("/user/db-info")
    @Operation(summary = "Get database USER notification info")
    public ResponseEntity<?> getUserNotificationInfo(HttpServletRequest request) {
        UUID userId = extractUserIdFromToken(request);
        
        long unreadCount = notificationService.getUnreadCount(userId, NotificationAudience.USER);
        Object stats = notificationService.getNotificationStats(userId, NotificationAudience.USER);
        
        return ResponseEntity.ok(new Object() {
            public final long unreadCountVal = unreadCount;
            public final Object statsVal = stats;
            public final String message = "Check these values in your database (USER notifications only)";
            public final String audience = "USER";
        });
    }

    /**
     * Get database info about ADMIN notifications
     */
    @GetMapping("/admin/db-info")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get database ADMIN notification info")
    public ResponseEntity<?> getAdminNotificationInfo(HttpServletRequest request) {
        UUID adminUserId = extractUserIdFromToken(request);
        
        long unreadCount = notificationService.getUnreadCount(adminUserId, NotificationAudience.ADMIN);
        Object stats = notificationService.getNotificationStats(adminUserId, NotificationAudience.ADMIN);
        
        return ResponseEntity.ok(new Object() {
            public final long unreadCountVal = unreadCount;
            public final Object statsVal = stats;
            public final String message = "Check these values in your database (ADMIN notifications only)";
            public final String audience = "ADMIN";
        });
    }
}
