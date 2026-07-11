package com.docusphere.backend.notification.entity;

/**
 * Distinguishes user-facing notifications from admin-panel notifications.
 * This enum enforces role-based notification separation:
 *
 * <b>USER Notifications:</b>
 * - Shown in regular user interface (top bar, notification center)
 * - Includes: team invitations, document shares, team member changes, document updates
 * - Users see these when NOT on admin pages
 * - Queried via {@code /api/notifications}
 *
 * <b>ADMIN Notifications:</b>
 * - Shown in admin panel only (admin dashboard, admin pages)
 * - Represents an action log of admin activities (team creation, member management, document deletion)
 * - Only admins see these when on admin pages
 * - Queried via {@code /api/admin/notifications}
 *
 * <b>Separation Guarantee:</b>
 * - {@link NotificationType#isAdminType()} identifies ADMIN_ prefixed types
 * - {@link #resolve(NotificationType, NotificationAudience)} enforces audience based on type
 * - Repository queries MUST filter by audience to prevent mixing
 * - Controllers validate audience matches the user's current context
 *
 * Users with ADMIN role can access both audiences depending on context:
 * - User space → USER audience only
 * - Admin space → ADMIN audience only
 * - Never mix both audiences in one view
 */
public enum NotificationAudience {
    USER,
    ADMIN;

    /** STOMP user queue suffix (full path: {@code /user/queue/notifications-user}). */
    public String websocketQueue() {
        return this == ADMIN ? "/queue/notifications-admin" : "/queue/notifications-user";
    }

    /**
     * Derives the correct audience from notification type.
     * Admin action types always use ADMIN; everything else is USER.
     *
     * @param type the notification type (determines if it's admin-only)
     * @param explicit the explicitly requested audience
     * @return ADMIN if type is admin type, otherwise explicit audience (or USER if null)
     */
    public static NotificationAudience resolve(NotificationType type, NotificationAudience explicit) {
        if (type != null && type.isAdminType()) {
            return ADMIN;
        }
        return explicit != null ? explicit : USER;
    }
}
