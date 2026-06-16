package com.docusphere.backend.documentShare.service;

import com.docusphere.backend.Common.config.AppConfig;
import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.EmailService;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentShare.dto.CreateShareLinkRequest;
import com.docusphere.backend.documentShare.dto.CreateShareLinkResponse;
import com.docusphere.backend.documentShare.dto.SharedDocumentResponse;
import com.docusphere.backend.documentShare.entity.DocumentShare;
import com.docusphere.backend.documentShare.entity.DocumentSharePermission;
import com.docusphere.backend.documentShare.entity.ShareLinkType;
import com.docusphere.backend.documentShare.repository.DocumentShareRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentSharingService {

    private final DocumentRepository documentRepository;
    private final DocumentShareRepository documentShareRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AppConfig appConfig;
    private final long defaultShareExpiryHours;
    private final SecureRandom secureRandom = new SecureRandom();

    public DocumentSharingService(
            DocumentRepository documentRepository,
            DocumentShareRepository documentShareRepository,
            UserRepository userRepository,
            EmailService emailService,
            AppConfig appConfig,
            @Value("${app.share.default-expiry-hours:168}") long defaultShareExpiryHours
    ) {
        this.documentRepository = documentRepository;
        this.documentShareRepository = documentShareRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.appConfig = appConfig;
        this.defaultShareExpiryHours = defaultShareExpiryHours;
    }

    @Transactional
    public CreateShareLinkResponse createShareLink(Long requesterId, UUID documentId, CreateShareLinkRequest request) {
        Document document = requireActiveDocument(documentId);
        ensureOwner(document, requesterId);
        validateShareRequest(request);
        String normalizedEmail = normalizeOptionalEmail(request.getEmail());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = resolveExpiresAt(request.getExpiresAt(), now);

        String ownerName = resolveOwnerName(document.getOwnerId());
        DocumentShare share = DocumentShare.builder()
                .document(document)
                .token(generateToken())
                .permission(request.getPermission())
                .type(request.getType())
                .email(normalizedEmail)
                .expiresAt(expiresAt)
                .revoked(false)
                .createdBy(requesterId)
                .build();
        DocumentShare saved = documentShareRepository.save(share);

        String shareUrl = appConfig.getFrontendUrl() + "/share/" + saved.getToken();
        if (saved.getType() == ShareLinkType.EMAIL_INVITE && normalizedEmail != null) {
            emailService.sendDocumentShareEmail(normalizedEmail, document.getName(), ownerName, shareUrl);
        }

        return CreateShareLinkResponse.builder()
                .shareLinkId(saved.getId())
                .token(saved.getToken())
                .shareUrl(shareUrl)
                .permission(saved.getPermission())
                .type(saved.getType())
                .email(saved.getEmail())
                .expiresAt(saved.getExpiresAt())
                .build();
    }

    @Transactional(readOnly = true)
    public SharedDocumentResponse openSharedDocument(String token) {
        DocumentShare share = requireValidShareToken(token);
        Document document = requireActiveDocument(share.getDocument().getId());
        return SharedDocumentResponse.builder()
                .documentId(document.getId())
                .name(document.getName())
                .type(document.getType())
                .sizeBytes(document.getSizeBytes())
                .fileUrl(document.getFileUrl())
                .permission(share.getPermission())
                .canView(share.getPermission().canView())
                .canComment(share.getPermission().canComment())
                .canEdit(share.getPermission().canEdit())
                .passwordProtected(document.isPasswordProtected())
                .build();
    }

    @Transactional
    public void revokeShareLink(Long requesterId, UUID documentId, String token) {
        Document document = requireActiveDocument(documentId);
        ensureOwner(document, requesterId);
        DocumentShare share = documentShareRepository
                .findByToken(token)
                .orElseThrow(() -> new DocumentNotFoundException("Share link not found"));
        if (!share.getDocument().getId().equals(documentId)) {
            throw new UnauthorizedAccessException("Share token does not belong to this document");
        }
        share.setRevoked(true);
        documentShareRepository.save(share);
    }

    @Transactional
    public void deleteSharesByDocumentId(UUID documentId) {
        documentShareRepository.deleteByDocumentId(documentId);
    }

    @Transactional(readOnly = true)
    public Document checkReadAccessByShareToken(UUID documentId, String token) {
        DocumentShare share = requireValidShareToken(token);
        if (!share.getDocument().getId().equals(documentId)) {
            throw new UnauthorizedAccessException("Share token does not belong to this document");
        }
        Document document = requireActiveDocument(documentId);
        return document;
    }

    @Transactional(readOnly = true)
    public void requireCommentPermission(UUID documentId, String token) {
        DocumentShare share = requireValidShareToken(token);
        if (!share.getDocument().getId().equals(documentId)) {
            throw new UnauthorizedAccessException("Share token does not belong to this document");
        }
        if (!share.getPermission().canComment()) {
            throw new UnauthorizedAccessException("Comment permission is required");
        }
    }

    private DocumentShare requireValidShareToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidRequestException("share token is required");
        }
        DocumentShare share = documentShareRepository.findByToken(token.trim())
                .orElseThrow(() -> new DocumentNotFoundException("Share link not found"));
        if (share.isRevoked()) {
            throw new UnauthorizedAccessException("Share link was revoked");
        }
        if (documentShareRepository.isExpired(share, LocalDateTime.now())) {
            throw new UnauthorizedAccessException("Share link expired");
        }
        if (share.getType() == ShareLinkType.EMAIL_INVITE && share.getEmail() == null) {
            throw new InvalidRequestException("Invalid invite share link");
        }
        return share;
    }

    private void validateShareRequest(CreateShareLinkRequest request) {
        if (request.getType() == ShareLinkType.EMAIL_INVITE && (request.getEmail() == null || request.getEmail().isBlank())) {
            throw new InvalidRequestException("email is required for EMAIL_INVITE share type");
        }
        if (request.getType() == ShareLinkType.PUBLIC && request.getEmail() != null && !request.getEmail().isBlank()) {
            throw new InvalidRequestException("email must be empty for PUBLIC share type");
        }
        if (request.getType() == ShareLinkType.PUBLIC && request.getPermission() == DocumentSharePermission.EDIT) {
            throw new InvalidRequestException("EDIT permission is only allowed for EMAIL_INVITE share type");
        }
    }

    private LocalDateTime resolveExpiresAt(LocalDateTime requestedExpiresAt, LocalDateTime now) {
        if (requestedExpiresAt != null) {
            if (!requestedExpiresAt.isAfter(now)) {
                throw new InvalidRequestException("expiresAt must be in the future");
            }
            return requestedExpiresAt;
        }

        if (defaultShareExpiryHours <= 0) {
            return null;
        }

        return now.plusHours(defaultShareExpiryHours);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeOptionalEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return normalizeEmail(email);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new InvalidRequestException("email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void validateInviteEmailForUser(UUID documentId, String token, String loggedInEmail) {
        DocumentShare share = requireValidShareToken(token);
        if (!share.getDocument().getId().equals(documentId)) {
            throw new UnauthorizedAccessException("Share token does not belong to this document");
        }
        if (share.getType() == ShareLinkType.EMAIL_INVITE) {
            if (loggedInEmail == null || loggedInEmail.isBlank()) {
                throw new UnauthorizedAccessException("Sign in with invited email to continue");
            }
            if (!normalizeEmail(loggedInEmail).equals(normalizeEmail(share.getEmail()))) {
                throw new UnauthorizedAccessException("Signed-in account does not match invited email");
            }
        }
    }

    private Document requireActiveDocument(UUID documentId) {
        return documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));
    }

    private void ensureOwner(Document document, Long requesterId) {
        if (requesterId == null || !document.getOwnerId().equals(requesterId)) {
            throw new UnauthorizedAccessException("Only the owner can perform this action");
        }
    }

    private String resolveOwnerName(Long ownerId) {
        return userRepository.findById(ownerId)
                .map(User::getFullName)
                .filter(name -> !name.isBlank())
                .orElse("Document owner");
    }
}
