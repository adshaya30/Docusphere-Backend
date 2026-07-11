package com.docusphere.backend.notification.service;

import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.documentShare.entity.DocumentShare;
import com.docusphere.backend.documentShare.entity.ShareLinkType;
import com.docusphere.backend.documentShare.repository.DocumentShareRepository;
import com.docusphere.backend.notification.util.NotificationUserIds;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.repository.TeamMemberRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves users who should receive <em>user-audience</em> document notifications
 * (owner, team members, email share invitees).
 */
@Service
public class DocumentNotificationRecipientService {

    private final TeamMemberRepository teamMemberRepository;
    private final DocumentShareRepository documentShareRepository;

    public DocumentNotificationRecipientService(TeamMemberRepository teamMemberRepository,
                                                  DocumentShareRepository documentShareRepository) {
        this.teamMemberRepository = teamMemberRepository;
        this.documentShareRepository = documentShareRepository;
    }

    /**
     * Users affected by a document change, excluding {@code excludeUserId} (the actor).
     */
    public Set<UUID> resolveAffectedUsers(Document document, Long excludeUserId) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (document == null) {
            return recipients;
        }

        Long ownerId = document.getOwnerId();
        if (ownerId != null && !ownerId.equals(excludeUserId)) {
            recipients.add(NotificationUserIds.fromUserId(ownerId));
        }

        UUID teamId = document.getTeamId();
        if (teamId != null) {
            for (TeamMember member : teamMemberRepository.findAllByTeamId(teamId)) {
                Long memberUserId = member.getUserId();
                if (memberUserId != null && !memberUserId.equals(excludeUserId)) {
                    recipients.add(NotificationUserIds.fromUserId(memberUserId));
                }
            }
        }

        for (DocumentShare share : documentShareRepository.findByDocumentId(document.getId())) {
            if (share.isRevoked()) {
                continue;
            }
            if (share.getType() == ShareLinkType.EMAIL_INVITE && share.getEmail() != null
                && !share.getEmail().isBlank()) {
                recipients.add(NotificationUserIds.fromEmail(share.getEmail().toLowerCase(Locale.ROOT)));
            }
        }

        return recipients;
    }

    public Set<UUID> resolveTeamMembersExcept(UUID teamId, Long excludeUserId) {
        Set<UUID> recipients = new LinkedHashSet<>();
        if (teamId == null) {
            return recipients;
        }
        for (TeamMember member : teamMemberRepository.findAllByTeamId(teamId)) {
            Long memberUserId = member.getUserId();
            if (memberUserId != null && !memberUserId.equals(excludeUserId)) {
                recipients.add(NotificationUserIds.fromUserId(memberUserId));
            }
        }
        return recipients;
    }
}
