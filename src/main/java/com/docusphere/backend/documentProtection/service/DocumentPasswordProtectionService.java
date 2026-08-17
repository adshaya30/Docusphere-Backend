package com.docusphere.backend.documentProtection.service;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.Common.exception.InvalidPasswordException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentProtection.dto.DocumentProtectionResponse;
import com.docusphere.backend.documentProtection.dto.PasswordVerificationResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.docusphere.backend.Common.util.PasswordValidator;

import java.util.UUID;

@Service
public class DocumentPasswordProtectionService {

    private final DocumentRepository documentRepository;
    private final PasswordEncoder passwordEncoder;
    private final DocumentPasswordAccessGuard accessGuard;
    private final DocumentPasswordVerificationStore verificationStore;
    private final PasswordValidator passwordValidator;

    public DocumentPasswordProtectionService(
            DocumentRepository documentRepository,
            PasswordEncoder passwordEncoder,
            DocumentPasswordAccessGuard accessGuard,
            DocumentPasswordVerificationStore verificationStore,
            PasswordValidator passwordValidator
    ) {
        this.documentRepository = documentRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessGuard = accessGuard;
        this.verificationStore = verificationStore;
        this.passwordValidator = passwordValidator;
    }

    @Transactional
    public DocumentProtectionResponse enableProtection(Long requesterId, UUID documentId, String password) {
        passwordValidator.validateOrThrow(password);
        Document document = requireActiveDocument(documentId);
        ensureOwner(document, requesterId);

        document.setPasswordProtected(true);
        document.setPasswordHash(passwordEncoder.encode(password));
        verificationStore.clearAllForDocument(documentId);

        Document saved = documentRepository.save(document);
        return toProtectionResponse(saved);
    }

    @Transactional
    public DocumentProtectionResponse removeProtection(Long requesterId, UUID documentId) {
        Document document = requireActiveDocument(documentId);
        ensureOwner(document, requesterId);

        document.setPasswordProtected(false);
        document.setPasswordHash(null);
        verificationStore.clearAllForDocument(documentId);

        Document saved = documentRepository.save(document);
        return toProtectionResponse(saved);
    }

    @Transactional(readOnly = true)
    public PasswordVerificationResponse verifyPassword(
            Long requesterId,
            UUID documentId,
            String password,
            String shareToken
    ) {
        Document document = requireActiveDocument(documentId);

        if (!accessGuard.hasDocumentAccess(document, requesterId, shareToken)) {
            throw new UnauthorizedAccessException("You do not have access to this document");
        }

        if (!document.isPasswordProtected()) {
            return PasswordVerificationResponse.builder()
                    .documentId(documentId)
                    .verified(true)
                    .build();
        }

        if (document.getPasswordHash() == null
                || !passwordEncoder.matches(password, document.getPasswordHash())) {
            throw new InvalidPasswordException("Incorrect document password");
        }

        String principalKey = requesterId != null
                ? "user:" + requesterId
                : "share:" + (shareToken != null ? shareToken.trim() : "");
        verificationStore.markVerified(documentId, principalKey);

        return PasswordVerificationResponse.builder()
                .documentId(documentId)
                .verified(true)
                .build();
    }

    @Transactional
    public DocumentProtectionResponse resetProtectionPassword(Long requesterId, UUID documentId, String newPassword) {
        passwordValidator.validateOrThrow(newPassword);
        Document document = requireActiveDocument(documentId);
        ensureOwner(document, requesterId);

        document.setPasswordProtected(true);
        document.setPasswordHash(passwordEncoder.encode(newPassword));
        verificationStore.clearAllForDocument(documentId);

        Document saved = documentRepository.save(document);
        return toProtectionResponse(saved);
    }

    public void requirePasswordForContentAccess(
            Document document,
            String password,
            Long requesterId,
            String shareToken
    ) {
        if (!accessGuard.hasDocumentAccess(document, requesterId, shareToken)) {
            throw new UnauthorizedAccessException("You do not have access to this document");
        }
        accessGuard.requirePasswordAccess(document, password, requesterId, shareToken);
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

    // password validation is delegated to PasswordValidator component

    private DocumentProtectionResponse toProtectionResponse(Document document) {
        return DocumentProtectionResponse.builder()
                .documentId(document.getId())
                .name(document.getName())
                .passwordProtected(document.isPasswordProtected())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
