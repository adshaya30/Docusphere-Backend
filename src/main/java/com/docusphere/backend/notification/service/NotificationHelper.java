package com.docusphere.backend.notification.service;

import com.docusphere.backend.notification.entity.NotificationType;
import com.docusphere.backend.notification.event.NotificationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Utility service for common notification scenarios.
 * Provides convenient methods for triggering notifications for common application events.
 */
@Service
public class NotificationHelper {

    private final NotificationEventPublisher notificationEventPublisher;

    public NotificationHelper(NotificationEventPublisher notificationEventPublisher) {
        this.notificationEventPublisher = notificationEventPublisher;
    }

    // ========== Team Notifications ==========

    /**
     * Notify user of team invitation
     */
    public void notifyTeamInvitation(UUID userId, UUID teamId, String teamName, UUID invitationId, String inviterName) {
        String metadata = invitationId != null ? String.format("{\"invitationId\":\"%s\"}", invitationId) : null;
        notificationEventPublisher.publishNotificationEvent(
            userId,
            NotificationType.TEAM_INVITATION,
            "Team Invitation",
            String.format("%s has invited you to join team: %s", inviterName != null ? inviterName : "Someone", teamName),
            "team",
            teamId,
            metadata,
            "/teams/" + teamId
        );
    }

    /**
     * Notify inviter that invitation was accepted
     */
    public void notifyTeamInvitationAccepted(UUID inviterUserId, String teamName, String inviteeName) {
        notificationEventPublisher.publishNotificationEvent(
            inviterUserId,
            NotificationType.TEAM_INVITATION_ACCEPTED, // Or TEAM_MEMBER_ADDED if you prefer
            "Invitation Accepted",
            String.format("%s has accepted your invitation and joined team: %s", inviteeName, teamName),
            "team",
            null,
            null,
            null
        );
    }

    /**
     * Notify inviter that invitation was declined
     */
    public void notifyTeamInvitationDeclined(UUID inviterUserId, String teamName, String inviteeName) {
        notificationEventPublisher.publishNotificationEvent(
            inviterUserId,
            NotificationType.TEAM_INVITATION_DECLINED,
            "Invitation Declined",
            String.format("%s has declined your invitation to join team: %s", inviteeName, teamName),
            "team",
            null,
            null,
            null
        );
    }

    /**
     * Notify team members that a new user has joined
     */
    public void notifyTeamMemberAdded(Iterable<UUID> teamMemberIds, UUID teamId, String teamName, String newMemberName) {
        notificationEventPublisher.publishNotificationEventToUsers(
            teamMemberIds,
            NotificationType.TEAM_MEMBER_ADDED,
            "Team Member Added",
            String.format("%s has joined your team: %s", newMemberName, teamName),
            "team",
            teamId,
            null,
            "/teams/" + teamId
        );
    }

    /**
     * Notify team members that a user has been removed
     */
    public void notifyTeamMemberRemoved(Iterable<UUID> teamMemberIds, UUID teamId, String teamName, String removedMemberName) {
        notificationEventPublisher.publishNotificationEventToUsers(
            teamMemberIds,
            NotificationType.TEAM_MEMBER_REMOVED,
            "Team Member Removed",
            String.format("%s has been removed from your team: %s", removedMemberName, teamName),
            "team",
            teamId,
            null,
            "/teams/" + teamId
        );
    }

    /**
     * Notify user that their team has been deleted
     */
    public void notifyTeamDeleted(UUID userId, String teamName) {
        notificationEventPublisher.publishNotificationEvent(
            userId,
            NotificationType.TEAM_DELETED,
            "Team Deleted",
            String.format("Your team '%s' has been deleted", teamName),
            "team",
            null,
            null,
            null
        );
    }

    // ========== Document Notifications ==========

    /**
     * Notify user that a document has been shared with them
     */
    public void notifyDocumentShared(UUID userId, UUID documentId, String documentName, String sharedByName) {
        notificationEventPublisher.publishNotificationEvent(
            userId,
            NotificationType.DOCUMENT_SHARED,
            "Document Shared",
            String.format("%s shared document: %s", sharedByName, documentName),
            "document",
            documentId,
            null,
            "/documents/" + documentId
        );
    }

    /**
     * Notify users that document sharing has been revoked
     */
    public void notifyDocumentUnshared(Iterable<UUID> userIds, UUID documentId, String documentName) {
        notificationEventPublisher.publishNotificationEventToUsers(
            userIds,
            NotificationType.DOCUMENT_UNSHARED,
            "Document Unshared",
            String.format("Document sharing has been revoked: %s", documentName),
            "document",
            documentId,
            null,
            null
        );
    }

    /**
     * Notify user that a document has been updated
     */
    public void notifyDocumentUpdated(Iterable<UUID> userIds, UUID documentId, String documentName, String updatedByName) {
        notificationEventPublisher.publishNotificationEventToUsers(
            userIds,
            NotificationType.DOCUMENT_UPDATED,
            "Document Updated",
            String.format("%s updated document: %s", updatedByName, documentName),
            "document",
            documentId,
            null,
            "/documents/" + documentId
        );
    }

    /**
     * Notify user that a document has been uploaded successfully
     */
    public void notifyDocumentUploaded(UUID userId, UUID documentId, String documentName) {
        notificationEventPublisher.publishNotificationEvent(
            userId,
            NotificationType.DOCUMENT_UPLOADED,
            "Document Uploaded",
            String.format("Document '%s' has been uploaded successfully", documentName),
            "document",
            documentId,
            null,
            "/documents/" + documentId
        );
    }

    /**
     * Notify team members that a new document was uploaded (user top bar only).
     */
    public void notifyTeamDocumentUploaded(Iterable<UUID> userIds, UUID documentId, String documentName,
                                           String uploaderName) {
        notificationEventPublisher.publishNotificationEventToUsers(
            userIds,
            NotificationType.DOCUMENT_UPLOADED,
            "New Team Document",
            String.format("%s uploaded \"%s\" to your team.", uploaderName, documentName),
            "document",
            documentId,
            null,
            "/documents/" + documentId
        );
    }

    /**
     * Notify users that a document was deleted (user top bar — not admin action log).
     */
    public void notifyDocumentDeleted(Iterable<UUID> userIds, UUID documentId, String documentName,
                                      String deletedByLabel) {
        notificationEventPublisher.publishNotificationEventToUsers(
            userIds,
            NotificationType.DOCUMENT_DELETED,
            "Document Deleted",
            String.format("%s removed \"%s\".", deletedByLabel, documentName),
            "document",
            documentId,
            null,
            null
        );
    }

    /**
     * Notify user that document upload has failed
     */
    public void notifyUploadFailed(UUID userId, String documentName, String errorMessage) {
        notificationEventPublisher.publishNotificationEvent(
            userId,
            NotificationType.UPLOAD_FAILED,
            "Upload Failed",
            String.format("Failed to upload '%s': %s", documentName, errorMessage),
            "document",
            null,
            null,
            null
        );
    }

    // ========== Action/Task Notifications ==========

    /**
     * Notify user that they have been assigned an action/task
     */
    public void notifyActionAssigned(UUID userId, UUID actionId, String actionTitle, String documentName) {
        notificationEventPublisher.publishNotificationEvent(
            userId,
            NotificationType.DOCUMENT_ACTION_ASSIGNED,
            "Action Assigned",
            String.format("You have been assigned: %s on %s", actionTitle, documentName),
            "action",
            actionId,
            null,
            "/documents/" + actionId
        );
    }

    /**
     * Notify users that an action has been created
     */
    public void notifyActionCreated(Iterable<UUID> userIds, UUID actionId, String actionTitle, String createdByName) {
        notificationEventPublisher.publishNotificationEventToUsers(
            userIds,
            NotificationType.DOCUMENT_ACTION_CREATED,
            "Action Created",
            String.format("%s created action: %s", createdByName, actionTitle),
            "action",
            actionId,
            null,
            null
        );
    }

    /**
     * Notify users that an action has been completed
     */
    public void notifyActionCompleted(Iterable<UUID> userIds, UUID actionId, String actionTitle, String completedByName) {
        notificationEventPublisher.publishNotificationEventToUsers(
            userIds,
            NotificationType.DOCUMENT_ACTION_COMPLETED,
            "Action Completed",
            String.format("%s completed action: %s", completedByName, actionTitle),
            "action",
            actionId,
            null,
            null
        );
    }

    // ========== User Team Membership Notifications ==========

    /**
     * Notify user that they have been added to a team
     */
    public void notifyUserAddedToTeam(UUID userId, UUID teamId, String teamName) {
        notificationEventPublisher.publishNotificationEvent(
            userId,
            NotificationType.USER_ADDED_TO_TEAM,
            "Added to Team",
            String.format("You have been added to team: %s", teamName),
            "team",
            teamId,
            null,
            "/teams/" + teamId
        );
    }

    /**
     * Notify user that they have been removed from a team
     */
    public void notifyUserRemovedFromTeam(UUID userId, UUID teamId, String teamName) {
        notificationEventPublisher.publishNotificationEvent(
            userId,
            NotificationType.USER_REMOVED_FROM_TEAM,
            "Removed from Team",
            String.format("You have been removed from team: %s", teamName),
            "team",
            teamId,
            null,
            null
        );
    }

    /**
     * Notify user that their role in a team has been updated
     */
    public void notifyUserRoleUpdated(UUID userId, UUID teamId, String teamName, String newRole) {
        notificationEventPublisher.publishNotificationEvent(
            userId,
            NotificationType.USER_ROLE_UPDATED,
            "Role Updated",
            String.format("Your role in team '%s' has been updated to %s", teamName, newRole),
            "team",
            teamId,
            null,
            "/teams/" + teamId
        );
    }

    // ========== Team Archiving Notifications ==========

    /**
     * Notify team members that their team has been archived
     */
    public void notifyTeamArchived(UUID userId, UUID teamId, String teamName, String reason) {
        notificationEventPublisher.publishNotificationEvent(
            userId,
            NotificationType.TEAM_ARCHIVED,
            "Team Archived",
            String.format("Team '%s' has been archived. Reason: %s", teamName, reason),
            "team",
            teamId,
            null,
            "/teams/" + teamId
        );
    }

    /**
     * Notify team members that their team has been restored from archive
     */
    public void notifyTeamRestored(UUID userId, UUID teamId, String teamName) {
        notificationEventPublisher.publishNotificationEvent(
            userId,
            NotificationType.TEAM_RESTORED,
            "Team Restored",
            String.format("Team '%s' has been restored and is now active", teamName),
            "team",
            teamId,
            null,
            "/teams/" + teamId
        );
    }
}
