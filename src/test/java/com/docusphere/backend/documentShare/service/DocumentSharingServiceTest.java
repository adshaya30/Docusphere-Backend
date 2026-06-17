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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentSharingService Unit Tests")
class DocumentSharingServiceTest {

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

    @Captor
    private ArgumentCaptor<DocumentShare> documentShareCaptor;

    private DocumentSharingService documentSharingService;

    private UUID documentId;
    private Long requesterId;
    private Document mockDocument;
    private User mockUser;

    @BeforeEach
    void setUp() {
        // Create service with all required parameters
        documentSharingService = new DocumentSharingService(
                documentRepository,
                documentShareRepository,
                userRepository,
                emailService,
                appConfig,
                168L  // defaultShareExpiryHours (7 days)
        );

        documentId = UUID.randomUUID();
        requesterId = 123L;

        mockDocument = Document.builder()
                .id(documentId)
                .fileId("file123")
                .name("Test Document")
                .type("pdf")
                .sizeBytes(1024L)
                .ownerId(requesterId)
                .storageKey("storage/key")
                .fileUrl("http://example.com/file")
                .deleted(false)
                .build();

        mockUser = new User();
        mockUser.setId(requesterId);
        mockUser.setEmail("owner@example.com");
        mockUser.setFullName("Owner User");
    }

    // ==================== CREATE SHARE LINK TESTS ====================

    @Test
    @DisplayName("Should successfully create public share link")
    void testCreateShareLink_Public_Success() {
        // Arrange
        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.PUBLIC);
        request.setPermission(DocumentSharePermission.VIEW);

        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(userRepository.findById(requesterId))
                .thenReturn(Optional.of(mockUser));
        
        DocumentShare savedShare = DocumentShare.builder()
                .id(UUID.randomUUID())
                .document(mockDocument)
                .token("test-token-123")
                .permission(DocumentSharePermission.VIEW)
                .type(ShareLinkType.PUBLIC)
                .email(null)
                .expiresAt(null)
                .revoked(false)
                .createdBy(requesterId)
                .build();

        when(documentShareRepository.save(any())).thenReturn(savedShare);
        when(appConfig.getFrontendUrl()).thenReturn("http://localhost:5173");

        // Act
        CreateShareLinkResponse response = documentSharingService.createShareLink(
                requesterId, documentId, request
        );

        // Assert
        assertNotNull(response);
        assertEquals("test-token-123", response.getToken());
        assertEquals(DocumentSharePermission.VIEW, response.getPermission());
        assertEquals(ShareLinkType.PUBLIC, response.getType());
        assertNull(response.getEmail());
        assertTrue(response.getShareUrl().contains("test-token-123"));

        verify(documentRepository, times(1)).findByIdAndDeletedFalse(documentId);
        verify(documentShareRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should reject EDIT permission for public share links")
    void testCreateShareLink_Public_EditPermission_ThrowsException() {
        // Arrange
        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.PUBLIC);
        request.setPermission(DocumentSharePermission.EDIT);

        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentSharingService.createShareLink(requesterId, documentId, request)
        );

        assertEquals("EDIT permission is only allowed for EMAIL_INVITE share type", exception.getMessage());
        verify(documentShareRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully create email invite share link")
    void testCreateShareLink_EmailInvite_Success() {
        // Arrange
        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.EMAIL_INVITE);
        request.setPermission(DocumentSharePermission.COMMENT);
        request.setEmail("recipient@example.com");

        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(userRepository.findById(requesterId))
                .thenReturn(Optional.of(mockUser));

        DocumentShare savedShare = DocumentShare.builder()
                .id(UUID.randomUUID())
                .document(mockDocument)
                .token("email-token-456")
                .permission(DocumentSharePermission.COMMENT)
                .type(ShareLinkType.EMAIL_INVITE)
                .email("recipient@example.com")
                .expiresAt(null)
                .revoked(false)
                .createdBy(requesterId)
                .build();

        when(documentShareRepository.save(any())).thenReturn(savedShare);
        when(appConfig.getFrontendUrl()).thenReturn("http://localhost:5173");

        // Act
        CreateShareLinkResponse response = documentSharingService.createShareLink(
                requesterId, documentId, request
        );

        // Assert
        assertNotNull(response);
        assertEquals("recipient@example.com", response.getEmail());
        assertEquals(ShareLinkType.EMAIL_INVITE, response.getType());

        verify(emailService, times(1)).sendDocumentShareEmail(
                anyString(), anyString(), anyString(), anyString()
        );
    }

    @Test
    @DisplayName("Should successfully create email invite share link with EDIT permission")
    void testCreateShareLink_EmailInvite_EditPermission_Success() {
        // Arrange
        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.EMAIL_INVITE);
        request.setPermission(DocumentSharePermission.EDIT);
        request.setEmail("editor@example.com");

        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(userRepository.findById(requesterId))
                .thenReturn(Optional.of(mockUser));

        DocumentShare savedShare = DocumentShare.builder()
                .id(UUID.randomUUID())
                .document(mockDocument)
                .token("edit-token-789")
                .permission(DocumentSharePermission.EDIT)
                .type(ShareLinkType.EMAIL_INVITE)
                .email("editor@example.com")
                .expiresAt(null)
                .revoked(false)
                .createdBy(requesterId)
                .build();

        when(documentShareRepository.save(any())).thenReturn(savedShare);
        when(appConfig.getFrontendUrl()).thenReturn("http://localhost:5173");

        // Act
        CreateShareLinkResponse response = documentSharingService.createShareLink(
                requesterId, documentId, request
        );

        // Assert
        assertNotNull(response);
        assertEquals(DocumentSharePermission.EDIT, response.getPermission());
        assertEquals(ShareLinkType.EMAIL_INVITE, response.getType());
        assertEquals("editor@example.com", response.getEmail());
    }

    @Test
    @DisplayName("Should throw exception when document is deleted")
    void testCreateShareLink_DeletedDocument_ThrowsException() {
        // Arrange
        mockDocument.setDeleted(true);
        
        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.PUBLIC);
        request.setPermission(DocumentSharePermission.VIEW);

        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.empty());

        // Act & Assert
        DocumentNotFoundException exception = assertThrows(
                DocumentNotFoundException.class,
                () -> documentSharingService.createShareLink(requesterId, documentId, request)
        );

        assertEquals("Document not found", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when user is not the owner")
    void testCreateShareLink_NotOwner_ThrowsException() {
        // Arrange
        mockDocument.setOwnerId(999L);

        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.PUBLIC);
        request.setPermission(DocumentSharePermission.VIEW);

        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));

        // Act & Assert
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> documentSharingService.createShareLink(requesterId, documentId, request)
        );

        assertEquals("Only the owner can perform this action", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when email invite missing email")
    void testCreateShareLink_EmailInviteNoEmail_ThrowsException() {
        // Arrange
        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.EMAIL_INVITE);
        request.setPermission(DocumentSharePermission.VIEW);
        request.setEmail(null);

        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentSharingService.createShareLink(requesterId, documentId, request)
        );

        assertTrue(exception.getMessage().contains("email is required"));
    }

    @Test
    @DisplayName("Should throw exception when expiry date is in the past")
    void testCreateShareLink_ExpiredDate_ThrowsException() {
        // Arrange
        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.PUBLIC);
        request.setPermission(DocumentSharePermission.VIEW);
        request.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentSharingService.createShareLink(requesterId, documentId, request)
        );

        assertTrue(exception.getMessage().contains("in the past") || 
                   exception.getMessage().contains("expir"));
    }

    // ==================== OPEN SHARED DOCUMENT TESTS ====================

    @Test
    @DisplayName("Should successfully open shared document")
    void testOpenSharedDocument_Success() {
        // Arrange
        String shareToken = "valid-token-123";
        LocalDateTime futureDate = LocalDateTime.now().plusDays(7);

        DocumentShare documentShare = DocumentShare.builder()
                .id(UUID.randomUUID())
                .document(mockDocument)
                .token(shareToken)
                .permission(DocumentSharePermission.VIEW)
                .type(ShareLinkType.PUBLIC)
                .revoked(false)
                .expiresAt(futureDate)
                .build();

        when(documentShareRepository.findByToken(shareToken))
                .thenReturn(Optional.of(documentShare));
        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(documentShareRepository.isExpired(any(DocumentShare.class), any(LocalDateTime.class)))
                .thenReturn(false);

        // Act
        SharedDocumentResponse response = documentSharingService.openSharedDocument(shareToken);

        // Assert
        assertNotNull(response);
        assertEquals(documentId, response.getDocumentId());
        assertEquals("Test Document", response.getName());
        assertEquals(DocumentSharePermission.VIEW, response.getPermission());
        assertTrue(response.isCanView());
        assertFalse(response.isCanComment());
        assertFalse(response.isCanEdit());
    }

    @Test
    @DisplayName("Should expose view, comment, and edit capabilities for EDIT shares")
    void testOpenSharedDocument_EditPermissionCapabilities() {
        // Arrange
        String shareToken = "edit-token-123";

        DocumentShare documentShare = DocumentShare.builder()
                .id(UUID.randomUUID())
                .document(mockDocument)
                .token(shareToken)
                .permission(DocumentSharePermission.EDIT)
                .type(ShareLinkType.EMAIL_INVITE)
                .email("editor@example.com")
                .revoked(false)
                .build();

        when(documentShareRepository.findByToken(shareToken))
                .thenReturn(Optional.of(documentShare));
        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(documentShareRepository.isExpired(any(DocumentShare.class), any(LocalDateTime.class)))
                .thenReturn(false);

        // Act
        SharedDocumentResponse response = documentSharingService.openSharedDocument(shareToken);

        // Assert
        assertNotNull(response);
        assertEquals(DocumentSharePermission.EDIT, response.getPermission());
        assertTrue(response.isCanView());
        assertTrue(response.isCanComment());
        assertTrue(response.isCanEdit());
    }

    @Test
    @DisplayName("Should throw exception when share token is invalid")
    void testOpenSharedDocument_InvalidToken_ThrowsException() {
        // Arrange
        String invalidToken = "invalid-token";

        when(documentShareRepository.findByToken(invalidToken))
                .thenReturn(Optional.empty());

        // Act & Assert
        DocumentNotFoundException exception = assertThrows(
                DocumentNotFoundException.class,
                () -> documentSharingService.openSharedDocument(invalidToken)
        );

        assertEquals("Share link not found", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when share link is revoked")
    void testOpenSharedDocument_RevokedLink_ThrowsException() {
        // Arrange
        String shareToken = "revoked-token";

        DocumentShare documentShare = DocumentShare.builder()
                .id(UUID.randomUUID())
                .document(mockDocument)
                .token(shareToken)
                .permission(DocumentSharePermission.VIEW)
                .revoked(true)
                .build();

        when(documentShareRepository.findByToken(shareToken))
                .thenReturn(Optional.of(documentShare));

        // Act & Assert
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> documentSharingService.openSharedDocument(shareToken)
        );

        assertEquals("Share link was revoked", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when share link is expired")
    void testOpenSharedDocument_ExpiredLink_ThrowsException() {
        // Arrange
        String shareToken = "expired-token";

        DocumentShare documentShare = DocumentShare.builder()
                .id(UUID.randomUUID())
                .document(mockDocument)
                .token(shareToken)
                .permission(DocumentSharePermission.VIEW)
                .revoked(false)
                .expiresAt(LocalDateTime.now().minusDays(1))
                .build();

        when(documentShareRepository.findByToken(shareToken))
                .thenReturn(Optional.of(documentShare));
        when(documentShareRepository.isExpired(any(DocumentShare.class), any(LocalDateTime.class)))
                .thenReturn(true);

        // Act & Assert
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> documentSharingService.openSharedDocument(shareToken)
        );

        assertEquals("Share link expired", exception.getMessage());
    }

    @Test
    @DisplayName("Should allow comment permission checks for EDIT shares")
    void testRequireCommentPermission_EditShare_Succeeds() {
        // Arrange
        String shareToken = "commentable-edit-token";

        DocumentShare documentShare = DocumentShare.builder()
                .id(UUID.randomUUID())
                .document(mockDocument)
                .token(shareToken)
                .permission(DocumentSharePermission.EDIT)
                .revoked(false)
                .build();

        when(documentShareRepository.findByToken(shareToken))
                .thenReturn(Optional.of(documentShare));
        when(documentShareRepository.isExpired(any(DocumentShare.class), any(LocalDateTime.class)))
                .thenReturn(false);

        // Act & Assert
        assertDoesNotThrow(() -> documentSharingService.requireCommentPermission(documentId, shareToken));
    }

    // ==================== REVOKE SHARE LINK TESTS ====================

    @Test
    @DisplayName("Should successfully revoke share link")
    void testRevokeShareLink_Success() {
        // Arrange
        String shareToken = "token-to-revoke";

        DocumentShare documentShare = DocumentShare.builder()
                .id(UUID.randomUUID())
                .document(mockDocument)
                .token(shareToken)
                .permission(DocumentSharePermission.VIEW)
                .revoked(false)
                .build();

        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(documentShareRepository.findByToken(shareToken))
                .thenReturn(Optional.of(documentShare));
        when(documentShareRepository.save(any())).thenReturn(documentShare);

        // Act
        documentSharingService.revokeShareLink(requesterId, documentId, shareToken);

        // Assert
        verify(documentShareRepository, times(1)).save(documentShareCaptor.capture());
        DocumentShare savedShare = documentShareCaptor.getValue();
        assertTrue(savedShare.isRevoked());
    }

    @Test
    @DisplayName("Should throw exception when revoking non-existent share link")
    void testRevokeShareLink_NotFound_ThrowsException() {
        // Arrange
        String invalidToken = "non-existent-token";

        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(documentShareRepository.findByToken(invalidToken))
                .thenReturn(Optional.empty());

        // Act & Assert
        DocumentNotFoundException exception = assertThrows(
                DocumentNotFoundException.class,
                () -> documentSharingService.revokeShareLink(requesterId, documentId, invalidToken)
        );

        assertEquals("Share link not found", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when revoking share from wrong document")
    void testRevokeShareLink_WrongDocument_ThrowsException() {
        // Arrange
        String shareToken = "valid-token";
        UUID wrongDocumentId = UUID.randomUUID();

        DocumentShare documentShare = DocumentShare.builder()
                .id(UUID.randomUUID())
                .document(mockDocument)
                .token(shareToken)
                .permission(DocumentSharePermission.VIEW)
                .revoked(false)
                .build();

        when(documentRepository.findByIdAndDeletedFalse(wrongDocumentId))
                .thenReturn(Optional.of(Document.builder().id(wrongDocumentId).ownerId(requesterId).build()));
        when(documentShareRepository.findByToken(shareToken))
                .thenReturn(Optional.of(documentShare));

        // Act & Assert
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> documentSharingService.revokeShareLink(requesterId, wrongDocumentId, shareToken)
        );

        assertTrue(exception.getMessage().contains("does not belong to this document"));
    }

    // ==================== DELETE SHARES TESTS ====================

    @Test
    @DisplayName("Should successfully delete all shares for a document")
    void testDeleteSharesByDocumentId_Success() {
        // Arrange
        doNothing().when(documentShareRepository).deleteByDocumentId(documentId);

        // Act
        documentSharingService.deleteSharesByDocumentId(documentId);

        // Assert
        verify(documentShareRepository, times(1)).deleteByDocumentId(documentId);
    }

    // ==================== CHECK READ ACCESS TESTS ====================

    @Test
    @DisplayName("Should successfully check read access with valid share token")
    void testCheckReadAccessByShareToken_Success() {
        // Arrange
        String shareToken = "valid-token";

        DocumentShare documentShare = DocumentShare.builder()
                .id(UUID.randomUUID())
                .document(mockDocument)
                .token(shareToken)
                .permission(DocumentSharePermission.VIEW)
                .revoked(false)
                .build();

        when(documentShareRepository.findByToken(shareToken))
                .thenReturn(Optional.of(documentShare));
        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(documentShareRepository.isExpired(any(DocumentShare.class), any(LocalDateTime.class)))
                .thenReturn(false);

        // Act
        Document result = documentSharingService.checkReadAccessByShareToken(documentId, shareToken);

        // Assert
        assertNotNull(result);
        assertEquals(documentId, result.getId());
    }

    @Test
    @DisplayName("Should throw exception on invalid share token for read access")
    void testCheckReadAccessByShareToken_InvalidToken_ThrowsException() {
        // Arrange
        String invalidToken = null;

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentSharingService.checkReadAccessByShareToken(documentId, invalidToken)
        );

        assertTrue(exception.getMessage().contains("share token is required"));
    }
}








