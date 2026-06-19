package com.docusphere.backend.documentProtection.service;

import com.docusphere.backend.Common.exception.InvalidPasswordException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.Common.util.PasswordValidator;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentAction.service.TeamAccessValidator;
import com.docusphere.backend.documentProtection.dto.DocumentProtectionResponse;
import com.docusphere.backend.documentProtection.dto.PasswordVerificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentPasswordProtectionServiceTest {

    private static final String VALID_PASSWORD = "Secure1!";
    private static final String DOCUMENT_PASSWORD = "secure123";

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private TeamAccessValidator teamAccessValidator;

    private PasswordEncoder passwordEncoder;
    private PasswordValidator passwordValidator;
    private DocumentPasswordVerificationStore verificationStore;
    private DocumentPasswordAccessGuard accessGuard;
    private DocumentPasswordProtectionService service;

    private UUID documentId;
    private Document document;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        passwordValidator = new PasswordValidator();
        verificationStore = new DocumentPasswordVerificationStore();
        accessGuard = new DocumentPasswordAccessGuard(passwordEncoder, verificationStore, teamAccessValidator);
        service = new DocumentPasswordProtectionService(
                documentRepository,
                passwordEncoder,
                accessGuard,
                verificationStore,
                passwordValidator
        );

        documentId = UUID.randomUUID();
        document = Document.builder()
                .id(documentId)
                .fileId("file-1")
                .name("report.pdf")
                .type("application/pdf")
                .sizeBytes(100L)
                .ownerId(10L)
                .storageKey("Documents/file-1_report.pdf")
                .fileUrl("https://example.com/file")
                .secured(false)
                .deleted(false)
                .build();
    }

    @Test
    void enableProtection_shouldHashPasswordAndSetFlag() {
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentProtectionResponse response = service.enableProtection(10L, documentId, VALID_PASSWORD);

        assertTrue(response.isPasswordProtected());
        assertNotNull(document.getPasswordHash());
        assertFalse(document.getPasswordHash().contains(VALID_PASSWORD));
        assertTrue(passwordEncoder.matches(VALID_PASSWORD, document.getPasswordHash()));
    }

    @Test
    void enableProtection_shouldRejectWeakPassword() {
        assertThrows(InvalidRequestException.class,
                () -> service.enableProtection(10L, documentId, "weak"));
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void enableProtection_shouldRejectNonOwner() {
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));

        assertThrows(UnauthorizedAccessException.class,
                () -> service.enableProtection(99L, documentId, VALID_PASSWORD));
    }

    @Test
    void removeProtection_shouldClearHashAndFlag() {
        document.setPasswordProtected(true);
        document.setPasswordHash(passwordEncoder.encode(DOCUMENT_PASSWORD));
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentProtectionResponse response = service.removeProtection(10L, documentId);

        assertFalse(response.isPasswordProtected());
        assertNull(document.getPasswordHash());
        assertFalse(document.isPasswordProtected());
    }

    @Test
    void resetProtectionPassword_shouldUpdateHashAndKeepProtectionEnabled() {
        document.setPasswordProtected(true);
        document.setPasswordHash(passwordEncoder.encode(DOCUMENT_PASSWORD));
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentProtectionResponse response = service.resetProtectionPassword(10L, documentId, VALID_PASSWORD);

        assertTrue(response.isPasswordProtected());
        assertTrue(passwordEncoder.matches(VALID_PASSWORD, document.getPasswordHash()));
        assertFalse(passwordEncoder.matches(DOCUMENT_PASSWORD, document.getPasswordHash()));
    }

    @Test
    void resetProtectionPassword_shouldRejectWeakPassword() {
        assertThrows(InvalidRequestException.class,
                () -> service.resetProtectionPassword(10L, documentId, "short"));
        verify(documentRepository, never()).findByIdAndDeletedFalse(any());
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void resetProtectionPassword_shouldRejectNonOwner() {
        document.setPasswordProtected(true);
        document.setPasswordHash(passwordEncoder.encode(DOCUMENT_PASSWORD));
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));

        assertThrows(UnauthorizedAccessException.class,
                () -> service.resetProtectionPassword(99L, documentId, VALID_PASSWORD));
    }

    @Test
    void verifyPassword_shouldReturnVerifiedForOwner() {
        document.setPasswordProtected(true);
        document.setPasswordHash(passwordEncoder.encode(DOCUMENT_PASSWORD));
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));

        PasswordVerificationResponse response = service.verifyPassword(10L, documentId, DOCUMENT_PASSWORD, null);

        assertTrue(response.isVerified());
        assertEquals(documentId, response.getDocumentId());
    }

    @Test
    void verifyPassword_shouldRejectWrongPassword() {
        document.setPasswordProtected(true);
        document.setPasswordHash(passwordEncoder.encode(DOCUMENT_PASSWORD));
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));

        assertThrows(InvalidPasswordException.class,
                () -> service.verifyPassword(10L, documentId, "wrong", null));
    }

    @Test
    void requirePasswordForContentAccess_shouldAllowAfterVerification() {
        document.setPasswordProtected(true);
        document.setPasswordHash(passwordEncoder.encode(DOCUMENT_PASSWORD));
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));

        service.verifyPassword(10L, documentId, DOCUMENT_PASSWORD, null);

        assertDoesNotThrow(() ->
                service.requirePasswordForContentAccess(document, null, 10L, null)
        );
    }
}
