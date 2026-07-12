package com.docusphere.backend.notification.service;

import com.docusphere.backend.notification.entity.NotificationAudience;
import com.docusphere.backend.notification.entity.NotificationType;
import com.docusphere.backend.notification.event.NotificationEventPublisher;
import com.docusphere.backend.notification.util.NotificationUserIds;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Admin-panel notifications: an action log of what the administrator did on the platform.
 * Wording is intentionally different from user notifications (no "your team" phrasing).
 */
@Service
public class AdminNotificationHelper {

    private final NotificationEventPublisher notificationEventPublisher;

    public AdminNotificationHelper(NotificationEventPublisher notificationEventPublisher) {
        this.notificationEventPublisher = notificationEventPublisher;
    }

    public void notifyTeamCreated(Long adminUserId, UUID teamId, String teamName) {
        publish(adminUserId, NotificationType.ADMIN_TEAM_CREATED,
            "Admin · Team created",
            String.format("New team \"%s\" was created.", teamName),
            "team", teamId, "/admin/teams/" + teamId,
            metadata("team", "create", teamName));
    }

    public void notifyTeamDeleted(Long adminUserId, UUID teamId, String teamName) {
        publish(adminUserId, NotificationType.ADMIN_TEAM_DELETED,
            "Admin · Team deleted",
            String.format("Team \"%s\" was permanently deleted.", teamName),
            "team", teamId, "/admin/teams",
            metadata("team", "delete", teamName));
    }

    public void notifyTeamsMerged(Long adminUserId, UUID mergedTeamId, String mergedTeamName,
                                  String sourceName, String targetName) {
        publish(adminUserId, NotificationType.ADMIN_TEAM_MERGED,
            "Admin · Teams merged",
            String.format("Merged \"%s\" + \"%s\" → \"%s\".", sourceName, targetName, mergedTeamName),
            "team", mergedTeamId, "/admin/teams/" + mergedTeamId,
            metadata("team", "merge", mergedTeamName));
    }

    public void notifyMemberAdded(Long adminUserId, UUID teamId, String teamName, String memberName) {
        publish(adminUserId, NotificationType.ADMIN_MEMBER_ADDED,
            "Admin · Member added",
            String.format("Added %s to \"%s\".", memberName, teamName),
            "team", teamId, "/admin/teams/" + teamId + "/members",
            metadata("member", "add", teamName));
    }

    public void notifyMemberRemoved(Long adminUserId, UUID teamId, String teamName, String memberName) {
        publish(adminUserId, NotificationType.ADMIN_MEMBER_REMOVED,
            "Admin · Member removed",
            String.format("Removed %s from \"%s\".", memberName, teamName),
            "team", teamId, "/admin/teams/" + teamId + "/members",
            metadata("member", "remove", teamName));
    }

    public void notifyMemberRoleChanged(Long adminUserId, UUID teamId, String teamName,
                                        String memberName, String newRole) {
        publish(adminUserId, NotificationType.ADMIN_MEMBER_ROLE_CHANGED,
            "Admin · Role updated",
            String.format("Set %s as %s in \"%s\".", memberName, newRole, teamName),
            "team", teamId, "/admin/teams/" + teamId + "/members",
            metadata("member", "role_change", teamName));
    }

    public void notifyLeaderTransferred(Long adminUserId, UUID teamId, String teamName, String newLeaderName) {
        publish(adminUserId, NotificationType.ADMIN_LEADER_TRANSFERRED,
            "Admin · Leader changed",
            String.format("New leader for \"%s\": %s.", teamName, newLeaderName),
            "team", teamId, "/admin/teams/" + teamId + "/members",
            metadata("member", "transfer_leader", teamName));
    }

    public void notifyDocumentDeleted(Long adminUserId, UUID documentId, String documentName, String teamName) {
        String message = teamName != null
            ? String.format("Deleted \"%s\" from team \"%s\".", documentName, teamName)
            : String.format("Deleted document \"%s\".", documentName);
        publish(adminUserId, NotificationType.ADMIN_DOCUMENT_DELETED,
            "Admin · Document deleted",
            message,
            "document", documentId, "/admin/documents",
            metadata("document", "delete", documentName));
    }

    public void notifyUserRegistered(Long adminUserId, UUID userId, String userEmail, String userName) {
        publish(adminUserId, NotificationType.ADMIN_USER_REGISTERED,
            "Admin · New user registered",
            String.format("New user registered: %s (%s)", userName, userEmail),
            "user", userId, "/admin/users/" + userId,
            metadata("user", "register", userName));
    }

    public void notifyUserAccountDeleted(Long adminUserId, UUID userId, String userName, String userEmail) {
        publish(adminUserId, NotificationType.ADMIN_USER_ACCOUNT_DELETED,
            "Admin · User account deleted",
            String.format("User account deleted: %s (%s)", userName, userEmail),
            "user", userId, "/admin/users",
            metadata("user", "delete", userName));
    }

    public void notifyUserRoleChanged(Long adminUserId, UUID userId, String userName, String newRole, String teamName) {
        String message = teamName != null
            ? String.format("Updated %s role to %s in team \"%s\".", userName, newRole, teamName)
            : String.format("Updated %s role to %s.", userName, newRole);
        publish(adminUserId, NotificationType.ADMIN_USER_ROLE_CHANGED,
            "Admin · User role changed",
            message,
            "user", userId, "/admin/users/" + userId,
            metadata("user", "role_change", userName));
    }

    public void notifyTeamArchived(Long adminUserId, UUID teamId, String teamName, String reason) {
        String message = reason != null && !reason.isEmpty()
            ? String.format("Archived team \"%s\". Reason: %s", teamName, reason)
            : String.format("Archived team \"%s\".", teamName);
        publish(adminUserId, NotificationType.ADMIN_TEAM_ARCHIVED,
            "Admin · Team archived",
            message,
            "team", teamId, "/admin/teams/" + teamId,
            metadata("team", "archive", teamName));
    }

    public void notifyTeamRestored(Long adminUserId, UUID teamId, String teamName) {
        publish(adminUserId, NotificationType.ADMIN_TEAM_RESTORED,
            "Admin · Team restored",
            String.format("Restored team \"%s\" from archive.", teamName),
            "team", teamId, "/admin/teams/" + teamId,
            metadata("team", "restore", teamName));
    }

    public void notifyTeamPermanentlyDeleted(Long adminUserId, UUID teamId, String teamName) {
        publish(adminUserId, NotificationType.ADMIN_TEAM_PERMANENTLY_DELETED,
            "Admin · Team permanently deleted",
            String.format("Permanently deleted archived team \"%s\".", teamName),
            "team", teamId, "/admin/teams",
            metadata("team", "permanent_delete", teamName));
    }

    public void notifyTeamInvitationAccepted(Long adminUserId, String teamName, String inviteeName) {
        publish(adminUserId, NotificationType.ADMIN_INVITATION_ACCEPTED,
            "Admin · Invitation accepted",
            String.format("%s has accepted your invitation to join team \"%s\".", inviteeName, teamName),
            "team", null, null,
            metadata("team", "invitation_accepted", teamName));
    }

    public void notifyTeamInvitationDeclined(Long adminUserId, String teamName, String inviteeName) {
        publish(adminUserId, NotificationType.ADMIN_INVITATION_DECLINED,
            "Admin · Invitation declined",
            String.format("%s has declined your invitation to join team \"%s\".", inviteeName, teamName),
            "team", null, null,
            metadata("team", "invitation_declined", teamName));
    }

    /** JSON metadata for frontend icons, tabs, and styling */
    private static String metadata(String category, String action, String subject) {
        return String.format(
            "{\"scope\":\"admin\",\"category\":\"%s\",\"action\":\"%s\",\"subject\":\"%s\"}",
            category, action, escapeJson(subject));
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void publish(Long adminUserId, NotificationType type, String title, String message,
                         String relatedEntityType, UUID relatedEntityId, String actionUrl, String metadata) {
        if (adminUserId == null) {
            return;
        }
        notificationEventPublisher.publishNotificationEvent(
            NotificationUserIds.fromUserId(adminUserId),
            type,
            title,
            message,
            relatedEntityType,
            relatedEntityId,
            metadata,
            actionUrl,
            NotificationAudience.ADMIN
        );
    }
}
