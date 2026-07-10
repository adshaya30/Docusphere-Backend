package com.docusphere.backend.onlyoffice.service;

import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentEditPermissionService {

    private final TeamService teamService;

    /**
     * Checks if the user is authorized to view the document.
     */
    public boolean canView(Document document, Long userId) {
        if (document == null || userId == null) {
            return false;
        }
        if (document.getTeamId() == null) {
            // Personal Documents: only the Owner can View
            return document.getOwnerId().equals(userId);
        }
        // Team Documents: any member of the team can View
        return teamService.isUserInTeam(userId, document.getTeamId());
    }

    /**
     * Checks if the user is authorized to download the document.
     */
    public boolean canDownload(Document document, Long userId) {
        // Business rules: view and download permissions are identical
        return canView(document, userId);
    }

    /**
     * Checks if the user is authorized to edit the document via ONLYOFFICE.
     */
    public boolean canEdit(Document document, Long userId) {
        if (document == null || userId == null) {
            return false;
        }
        if (document.getTeamId() == null) {
            // Personal Documents: only the Owner can Edit
            return document.getOwnerId().equals(userId);
        }

        // Team Documents: Leader, Manager, and the Uploader (ownerId of the document) can Edit
        Optional<TeamMember> membershipOpt = teamService.findMembership(userId, document.getTeamId());
        if (membershipOpt.isEmpty()) {
            return false;
        }

        TeamMember member = membershipOpt.get();
        TeamRole role = member.getRole();

        if (role == TeamRole.LEADER || role == TeamRole.MANAGER) {
            return true;
        }

        if (role == TeamRole.MEMBER) {
            // All members can edit team documents
            return true;
        }

        return false;
    }

    /**
     * Checks if the user is authorized to restore a document version.
     * Personal documents: owner only.
     * Team documents: owner, team leader, or manager (not regular members).
     */
    public boolean canRestore(Document document, Long userId) {
        if (document == null || userId == null) {
            return false;
        }
        if (document.getTeamId() == null) {
            return document.getOwnerId().equals(userId);
        }

        if (document.getOwnerId().equals(userId)) {
            return true;
        }

        Optional<TeamMember> membershipOpt = teamService.findMembership(userId, document.getTeamId());
        if (membershipOpt.isEmpty()) {
            return false;
        }

        TeamRole role = membershipOpt.get().getRole();
        return role == TeamRole.LEADER || role == TeamRole.MANAGER;
    }
}