package com.docusphere.backend.documentProtection.service;

import com.docusphere.backend.Common.exception.DocumentPasswordRequiredException;
import com.docusphere.backend.Common.exception.InvalidPasswordException;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.documentAction.service.TeamAccessValidator;
import com.docusphere.backend.documentShare.service.DocumentSharingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enforces document password verification after base access (owner, team, share link) is granted.
 */
@Component
public class DocumentPasswordAccessGuard {

    private final PasswordEncoder passwordEncoder;
    private final DocumentPasswordVerificationStore verificationStore;
    private final TeamAccessValidator teamAccessValidator;
    @Autowired(required = false)
    private DocumentSharingService documentSharingService;

    public DocumentPasswordAccessGuard(
            PasswordEncoder passwordEncoder,
            DocumentPasswordVerificationStore verificationStore,
            TeamAccessValidator teamAccessValidator
    ) {
        this.passwordEncoder = passwordEncoder;
        this.verificationStore = verificationStore;
        this.teamAccessValidator = teamAccessValidator;
    }

    public void requirePasswordAccess(
            Document document,
            String password,
            Long requesterId,
            String shareToken
    ) {
        if (!document.isPasswordProtected()) {
            return;
        }

        if (hasVerifiedAccess(document.getId(), requesterId, shareToken)) {
            return;
        }

        if (password == null || password.isBlank()) {
            throw new DocumentPasswordRequiredException("Document password is required");
        }

        if (document.getPasswordHash() == null
                || !passwordEncoder.matches(password, document.getPasswordHash())) {
            throw new InvalidPasswordException("Incorrect document password");
        }

        markVerifiedAccess(document.getId(), requesterId, shareToken);
    }

    public void markVerifiedAccess(UUID documentId, Long requesterId, String shareToken) {
        if (requesterId != null) {
            verificationStore.markVerified(documentId, userKey(requesterId));
        }
        if (shareToken != null && !shareToken.isBlank()) {
            verificationStore.markVerified(documentId, shareKey(shareToken));
        }
    }

    public boolean hasVerifiedAccess(UUID documentId, Long requesterId, String shareToken) {
        if (requesterId != null && verificationStore.isVerified(documentId, userKey(requesterId))) {
            return true;
        }
        return shareToken != null
                && !shareToken.isBlank()
                && verificationStore.isVerified(documentId, shareKey(shareToken));
    }

    public boolean hasDocumentAccess(Document document, Long requesterId, String shareToken) {
        if (requesterId != null && document.getOwnerId().equals(requesterId)) {
            return true;
        }

        UUID teamId = document.getTeamId();
        if (requesterId != null && teamId != null && teamAccessValidator.isMember(requesterId, teamId)) {
            return true;
        }

        if (shareToken != null && !shareToken.isBlank() && documentSharingService != null) {
            try {
                documentSharingService.checkReadAccessByShareToken(document.getId(), shareToken);
                return true;
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        return false;
    }

    private String resolvePrincipalKey(Long requesterId, String shareToken) {
        if (requesterId != null) {
            return userKey(requesterId);
        }
        if (shareToken != null && !shareToken.isBlank()) {
            return shareKey(shareToken);
        }
        return "anonymous";
    }

    private String userKey(Long requesterId) {
        return "user:" + requesterId;
    }

    private String shareKey(String shareToken) {
        return "share:" + shareToken.trim();
    }
}
