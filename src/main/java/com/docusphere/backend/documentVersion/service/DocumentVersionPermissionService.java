package com.docusphere.backend.documentVersion.service;

import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.documentShare.repository.DocumentShareRepository;
import com.docusphere.backend.onlyoffice.service.DocumentEditPermissionService;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DocumentVersionPermissionService {

    private final DocumentEditPermissionService editPermissionService;
    private final DocumentShareRepository documentShareRepository;
    private final UserRepository userRepository;
    private final TeamService teamService;

    public boolean canView(Document document, Long userId) {
        if (document == null || userId == null) {
            return false;
        }
        if (editPermissionService.canView(document, userId)) {
            return true;
        }
        return hasActiveShareInvite(document, userId);
    }

    public boolean canDownload(Document document, Long userId) {
        return canView(document, userId);
    }

    public boolean canRestore(Document document, Long userId) {
        return editPermissionService.canRestore(document, userId);
    }

    public boolean canEdit(Document document, Long userId) {
        return editPermissionService.canEdit(document, userId);
    }

    public String resolveEditorRole(Document document, Long editorUserId) {
        if (editorUserId == null) {
            return "UNKNOWN";
        }
        if (document.getOwnerId().equals(editorUserId)) {
            return "OWNER";
        }

        if (document.getTeamId() != null) {
            Optional<TeamMember> membership = teamService.findMembership(editorUserId, document.getTeamId());
            if (membership.isPresent()) {
                return membership.get().getRole().name();
            }
        }

        if (hasActiveShareInvite(document, editorUserId)) {
            return "SHARED_USER";
        }

        return userRepository.findById(editorUserId)
                .map(User::getRole)
                .map(role -> role.getName() != null ? role.getName().replace("ROLE_", "") : "USER")
                .orElse("USER");
    }

    private boolean hasActiveShareInvite(Document document, Long userId) {
        if (document.getTeamId() != null) {
            return false;
        }

        return userRepository.findById(userId)
                .map(User::getEmail)
                .flatMap(email -> documentShareRepository
                        .findByDocumentIdAndEmailIgnoreCaseAndRevokedFalse(document.getId(), email))
                .filter(share -> !documentShareRepository.isExpired(share, LocalDateTime.now()))
                .isPresent();
    }
}
