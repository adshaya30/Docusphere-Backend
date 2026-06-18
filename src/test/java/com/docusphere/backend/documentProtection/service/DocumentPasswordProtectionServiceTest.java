package com.docusphere.backend.documentProtection.service;

import com.docusphere.backend.Common.exception.InvalidPasswordException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
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
import com.docusphere.backend.Common.util.PasswordValidator;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentPasswordProtectionServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private TeamAccessValidator teamAccessValidator;

    @Mock
    private PasswordValidator passwordValidator;

    private PasswordEncoder passwordEncoder;
    private DocumentPasswordVerificationStore verificationStore;
    private DocumentPasswordAccessGuard accessGuard;
    private DocumentPasswordProtectionService service;

    private UUID documentId;
    private Document document;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
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

        DocumentProtectionResponse response = service.enableProtection(10L, documentId, "secure123");

        assertTrue(response.isPasswordProtected());
        assertNotNull(document.getPasswordHash());
        assertFalse(document.getPasswordHash().contains("secure123"));
        assertTrue(passwordEncoder.matches("secure123", document.getPasswordHash()));
    }

    @Test
    void enableProtection_shouldRejectNonOwner() {
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));

        assertThrows(UnauthorizedAccessException.class,
                () -> service.enableProtection(99L, documentId, "secure123"));
    }

    @Test
    void removeProtection_shouldClearHashAndFlag() {
        document.setPasswordProtected(true);
        document.setPasswordHash(passwordEncoder.encode("secure123"));
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentProtectionResponse response = service.removeProtection(10L, documentId);

        assertFalse(response.isPasswordProtected());
        assertNull(document.getPasswordHash());
        assertFalse(document.isPasswordProtected());
    }

    @Test
    void verifyPassword_shouldReturnVerifiedForOwner() {
        document.setPasswordProtected(true);
        document.setPasswordHash(passwordEncoder.encode("secure123"));
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));

        PasswordVerificationResponse response = service.verifyPassword(10L, documentId, "secure123", null);

        assertTrue(response.isVerified());
        assertEquals(documentId, response.getDocumentId());
    }

    @Test
    void verifyPassword_shouldRejectWrongPassword() {
        document.setPasswordProtected(true);
        document.setPasswordHash(passwordEncoder.encode("secure123"));
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));

        assertThrows(InvalidPasswordException.class,
                () -> service.verifyPassword(10L, documentId, "wrong", null));
    }

    @Test
    void requirePasswordForContentAccess_shouldAllowAfterVerification() {
        document.setPasswordProtected(true);
        document.setPasswordHash(passwordEncoder.encode("secure123"));
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));

        service.verifyPassword(10L, documentId, "secure123", null);

        assertDoesNotThrow(() ->
                service.requirePasswordForContentAccess(document, null, 10L, null)
        );
    }
}
