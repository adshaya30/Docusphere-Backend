package com.docusphere.backend.notification.entity;

/**
 * Enum for different types of notifications in the Docusphere system.
 *
 * <b>IMPORTANT: Role-Based Notification Separation</b>
 *
 * This enum maintains two distinct notification categories to enforce role-based visibility:
 *
 * <b>USER Notifications (Regular user audience):</b>
 * - Shown in user interface (top bar, notification center)
 * - Examples: team invites, document shares, member changes, action assignments
 * - These notifications are NOT prefixed with "ADMIN_"
 * - Appear in {@code /api/notifications} endpoint
 * - Mapped to WebSocket queue: {@code /queue/notifications-user}
 *
 * <b>ADMIN Notifications (Admin-only audience):</b>
 * - Shown ONLY in admin panel (admin pages, admin dashboard)
 * - Represents an audit log of admin actions: team creation, member management, document deletion
 * - These notifications ARE prefixed with "ADMIN_" (e.g., ADMIN_TEAM_CREATED)
 * - Appear in {@code /api/admin/notifications} endpoint (protected by @PreAuthorize("hasRole('ADMIN')"))
 * - Mapped to WebSocket queue: {@code /queue/notifications-admin}
 *
 * <b>Enforcement:</b>
 * - {@link #isAdminType()} identifies ADMIN_ prefixed types
 * - {@link NotificationAudience#resolve(NotificationType, NotificationAudience)} automatically
 *   routes ADMIN types to ADMIN audience
 * - Attempts to mix audiences are prevented by repository filters and controller validations
 *
 * <b>CRITICAL: Do not mix these categories</b>
 * - Admin in user context = Security violation
 * - User in admin context = Incorrect audit trail
 * - Always query with explicit audience filter
 */
public enum NotificationType {
    // Team-related notifications (User audience)
    TEAM_INVITATION("Team Invitation", "You have been invited to a team"),
    TEAM_INVITATION_ACCEPTED("Invitation Accepted", "A user has accepted your team invitation"),
    TEAM_INVITATION_DECLINED("Invitation Declined", "A user has declined your team invitation"),
    TEAM_MEMBER_ADDED("Team Member Added", "A new member has been added to your team"),
    TEAM_MEMBER_REMOVED("Team Member Removed", "A member has been removed from your team"),
    TEAM_DELETED("Team Deleted", "Your team has been deleted"),
    
    // Document-related notifications (User audience)
    DOCUMENT_SHARED("Document Shared", "A document has been shared with you"),
    DOCUMENT_UNSHARED("Document Unshared", "Document sharing has been revoked"),
    DOCUMENT_STARRED("Document Starred", "A document has been starred"),
    DOCUMENT_UNSTARRED("Document Unstarred", "A document has been unstarred"),
    DOCUMENT_DELETED("Document Deleted", "A document has been deleted"),
    DOCUMENT_UPDATED("Document Updated", "A document has been updated"),
    
    // Action-related notifications (User audience)
    DOCUMENT_ACTION_CREATED("Document Action Created", "A new action has been created on a document"),
    DOCUMENT_ACTION_UPDATED("Document Action Updated", "An action has been updated"),
    DOCUMENT_ACTION_ASSIGNED("Action Assigned", "You have been assigned a task"),
    DOCUMENT_ACTION_COMPLETED("Action Completed", "An action has been completed"),
    
    // System notifications (User audience)
    DOCUMENT_UPLOADED("Document Uploaded", "A document has been uploaded successfully"),
    SYSTEM_NOTIFICATION("System Notification", "System notification"),
    UPLOAD_FAILED("Upload Failed", "Document upload has failed"),
    USER_ADDED_TO_TEAM("Added to Team", "You have been added to a team"),
    USER_REMOVED_FROM_TEAM("Removed from Team", "You have been removed from a team"),
    USER_ROLE_UPDATED("Role Updated", "Your role has been updated"),
    TEAM_ARCHIVED("Team Archived", "Your team has been archived"),
    TEAM_RESTORED("Team Restored", "Your team has been restored"),

    // ===================================================================
    // ADMIN-PANEL NOTIFICATIONS (audience = ADMIN) - Prefixed with ADMIN_
    // Only for admin users in admin context. Shows admin actions only.
    // ===================================================================
    ADMIN_TEAM_CREATED("Team Created", "You created a new team"),
    ADMIN_TEAM_DELETED("Team Deleted", "You deleted a team"),
    ADMIN_TEAM_ARCHIVED("Team Archived", "You archived a team"),
    ADMIN_TEAM_RESTORED("Team Restored", "You restored an archived team"),
    ADMIN_TEAM_PERMANENTLY_DELETED("Team Permanently Deleted", "You permanently deleted an archived team"),
    ADMIN_TEAM_MERGED("Teams Merged", "You merged teams"),
    ADMIN_MEMBER_ADDED("Member Added", "You added a member to a team"),
    ADMIN_MEMBER_REMOVED("Member Removed", "You removed a member from a team"),
    ADMIN_MEMBER_ROLE_CHANGED("Member Role Changed", "You updated a team member role"),
    ADMIN_LEADER_TRANSFERRED("Leader Transferred", "You transferred team leadership"),
    ADMIN_DOCUMENT_DELETED("Document Deleted", "You permanently deleted a document"),
    ADMIN_USER_REGISTERED("New User Registered", "A new user has registered"),
    ADMIN_USER_ACCOUNT_DELETED("User Account Deleted", "A user account has been deleted"),
    ADMIN_USER_ROLE_CHANGED("User Role Changed", "A user role has been changed"),
    ADMIN_INVITATION_ACCEPTED("Invitation Accepted", "A user accepted your invitation"),
    ADMIN_INVITATION_DECLINED("Invitation Declined", "A user declined your invitation"),

    // ===================================================================
    // SYSTEM NOTIFICATIONS (audience = ADMIN) — auto-generated by the platform
    // These are NOT action-log entries. They are system-health and safety alerts.
    // ===================================================================

    /**
     * Fired when a single user deletes a large number of documents within a short time window.
     * Helps the admin detect accidental mass-deletion or malicious activity.
     */
    ADMIN_BULK_DELETION_ALERT("⚠️ Bulk Deletion Alert",
        "A user has deleted many documents in a short period"),

    /**
     * Fired when total platform storage usage crosses a warning threshold (70% or 90%).
     * Similar to Supabase storage limit warnings.
     */
    ADMIN_STORAGE_WARNING("⚠️ Storage Limit Warning",
        "Platform storage usage has reached a critical threshold");

    private final String title;
    private final String defaultMessage;

    NotificationType(String title, String defaultMessage) {
        this.title = title;
        this.defaultMessage = defaultMessage;
    }

    public String getTitle() {
        return title;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * Returns true if this is an admin-only notification type.
     * Admin types are those prefixed with "ADMIN_" and should never appear in user notification views.
     *
     * @return true if this type starts with "ADMIN_", indicating admin-panel only visibility
     */
    public boolean isAdminType() {
        return name().startsWith("ADMIN_");
    }
}
