package com.docusphere.backend.documentShare.service;

import com.docusphere.backend.Common.config.AppConfig;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.audit.service.AuditService;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.EmailService;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentShare.entity.DocumentShare;
import com.docusphere.backend.documentShare.entity.DocumentSharePermission;
import com.docusphere.backend.documentShare.entity.ShareLinkType;
import com.docusphere.backend.documentShare.repository.DocumentShareRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentSharingService Security Tests")
class DocumentSharingServiceSecurityTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentShareRepository documentShareRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private AppConfig appConfig;
    @Mock
    private AuditService auditService;

    private DocumentSharingService documentSharingService;

    private UUID documentId;
    private String shareToken;
    private Document document;
    private DocumentShare editInvite;

    @BeforeEach
    void setUp() {
        documentSharingService = new DocumentSharingService(
                documentRepository,
                documentShareRepository,
                userRepository,
                emailService,
                appConfig,
                auditService,
                168L
        );

        documentId = UUID.randomUUID();
        shareToken = "invite-token";
        document = Document.builder()
                .id(documentId)
                .name("Secure.docx")
                .ownerId(1L)
                .deleted(false)
                .build();
        editInvite = DocumentShare.builder()
                .document(document)
                .token(shareToken)
                .permission(DocumentSharePermission.EDIT)
                .type(ShareLinkType.EMAIL_INVITE)
                .email("invitee@example.com")
                .revoked(false)
                .build();
    }

    @Test
    @DisplayName("Should allow edit via share token without authentication")
    void requireEditPermission_shareTokenOnly() {
        mockValidInvite();

        DocumentShare result = documentSharingService.requireEditPermission(documentId, shareToken);

        assertEquals(DocumentSharePermission.EDIT, result.getPermission());
        assertEquals("invitee@example.com", result.getEmail());
    }

    @Test
    @DisplayName("Should reject edit when share permission is view-only")
    void requireEditPermission_viewOnlyShare() {
        editInvite.setPermission(DocumentSharePermission.VIEW);
        mockValidInvite();

        assertThrows(
                UnauthorizedAccessException.class,
                () -> documentSharingService.requireEditPermission(documentId, shareToken)
        );
    }

    @Test
    @DisplayName("Should return invited email when opening shared document")
    void openSharedDocument_returnsInvitedEmail() {
        mockValidInvite();

        var response = documentSharingService.openSharedDocument(shareToken);

        assertEquals("invitee@example.com", response.getInvitedEmail());
        assertEquals(true, response.isCanEdit());
    }

    @Test
    @DisplayName("Should hide direct file URL for password-protected shared documents")
    void openSharedDocument_hidesFileUrlWhenProtected() {
        document.setSecured(true);
        document.setPasswordHash("hash");
        mockValidInvite();

        var response = documentSharingService.openSharedDocument(shareToken);

        assertEquals(true, response.isPasswordProtected());
        assertNull(response.getFileUrl());
    }

    private void mockValidInvite() {
        when(documentShareRepository.findByToken(shareToken)).thenReturn(Optional.of(editInvite));
        when(documentShareRepository.isExpired(eq(editInvite), any())).thenReturn(false);
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
    }
}
