package com.docusphere.backend.documentShare.controller;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.comment.repository.CommentRepository;
import com.docusphere.backend.documentShare.dto.CreateShareLinkRequest;
import com.docusphere.backend.documentShare.dto.CreateShareLinkResponse;
import com.docusphere.backend.documentShare.dto.DocumentShareListItemResponse;
import com.docusphere.backend.documentShare.dto.SharedDocumentResponse;
import com.docusphere.backend.documentShare.entity.DocumentSharePermission;
import com.docusphere.backend.documentShare.entity.ShareLinkType;
import com.docusphere.backend.documentShare.service.DocumentSharingService;
import com.docusphere.backend.onlyoffice.service.OnlyOfficeEditorConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DocumentSharingController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("DocumentSharingController Unit Tests")
class DocumentSharingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentSharingService documentSharingService;

    @MockitoBean
    private OnlyOfficeEditorConfigService onlyOfficeEditorConfigService;

    @MockitoBean
    private CommentRepository commentRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private UUID documentId;
    private Long userId;
    private String authToken;

    @BeforeEach
    void setUp() {
        documentId = UUID.randomUUID();
        userId = 123L;
        authToken = "Bearer test-jwt-token";
    }

    // ==================== CREATE SHARE LINK TESTS ====================

    @Test
    @DisplayName("Should successfully create public share link")
    void testCreateShareLink_Public_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.PUBLIC);
        request.setPermission(DocumentSharePermission.VIEW);

        CreateShareLinkResponse response = CreateShareLinkResponse.builder()
                .shareLinkId(UUID.randomUUID())
                .token("public-share-token-123")
                .shareUrl("http://localhost:5173/share/public-share-token-123")
                .permission(DocumentSharePermission.VIEW)
                .type(ShareLinkType.PUBLIC)
                .email(null)
                .expiresAt(null)
                .build();

        when(documentSharingService.createShareLink(eq(userId), eq(documentId), any(CreateShareLinkRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/documents/{id}/share", documentId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("public-share-token-123"))
                .andExpect(jsonPath("$.data.permission").value("VIEW"))
                .andExpect(jsonPath("$.data.type").value("PUBLIC"));

        verify(documentSharingService, times(1)).createShareLink(eq(userId), eq(documentId), any(CreateShareLinkRequest.class));
    }

    @Test
    @DisplayName("Should successfully create email invite share link")
    void testCreateShareLink_EmailInvite_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.EMAIL_INVITE);
        request.setPermission(DocumentSharePermission.COMMENT);
        request.setEmail("recipient@example.com");

        CreateShareLinkResponse response = CreateShareLinkResponse.builder()
                .shareLinkId(UUID.randomUUID())
                .token("email-share-token-456")
                .shareUrl("http://localhost:5173/share/email-share-token-456")
                .permission(DocumentSharePermission.COMMENT)
                .type(ShareLinkType.EMAIL_INVITE)
                .email("recipient@example.com")
                .expiresAt(null)
                .build();

        when(documentSharingService.createShareLink(eq(userId), eq(documentId), any(CreateShareLinkRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/documents/{id}/share", documentId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("recipient@example.com"))
                .andExpect(jsonPath("$.data.type").value("EMAIL_INVITE"));
    }

    @Test
    @DisplayName("Should throw exception when document not found")
    void testCreateShareLink_DocumentNotFound_Returns404() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.PUBLIC);
        request.setPermission(DocumentSharePermission.VIEW);

        when(documentSharingService.createShareLink(eq(userId), eq(documentId), any(CreateShareLinkRequest.class)))
                .thenThrow(new DocumentNotFoundException("Document not found"));

        // Act & Assert
        mockMvc.perform(post("/api/documents/{id}/share", documentId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should throw exception when user is not owner")
    void testCreateShareLink_NotOwner_Unauthorized() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.PUBLIC);
        request.setPermission(DocumentSharePermission.VIEW);

        when(documentSharingService.createShareLink(eq(userId), eq(documentId), any(CreateShareLinkRequest.class)))
                .thenThrow(new UnauthorizedAccessException("Only the owner can perform this action"));

        // Act & Assert
        mockMvc.perform(post("/api/documents/{id}/share", documentId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should throw exception when missing required email for email invite")
    void testCreateShareLink_EmailInviteNoEmail_BadRequest() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.EMAIL_INVITE);
        request.setPermission(DocumentSharePermission.VIEW);
        request.setEmail(null);

        when(documentSharingService.createShareLink(eq(userId), eq(documentId), any(CreateShareLinkRequest.class)))
                .thenThrow(new InvalidRequestException("email is required for EMAIL_INVITE share type"));

        // Act & Assert
        mockMvc.perform(post("/api/documents/{id}/share", documentId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should throw exception when missing authorization header")
    void testCreateShareLink_NoAuthHeader_Unauthorized() throws Exception {
        // Act & Assert
        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.PUBLIC);
        request.setPermission(DocumentSharePermission.VIEW);

        mockMvc.perform(post("/api/documents/{id}/share", documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should create share link with expiration date")
    void testCreateShareLink_WithExpiration_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);

        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.PUBLIC);
        request.setPermission(DocumentSharePermission.VIEW);
        request.setExpiresAt(expiresAt);

        CreateShareLinkResponse response = CreateShareLinkResponse.builder()
                .shareLinkId(UUID.randomUUID())
                .token("expiring-token")
                .shareUrl("http://localhost:5173/share/expiring-token")
                .permission(DocumentSharePermission.VIEW)
                .type(ShareLinkType.PUBLIC)
                .email(null)
                .expiresAt(expiresAt)
                .build();

        when(documentSharingService.createShareLink(eq(userId), eq(documentId), any(CreateShareLinkRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/documents/{id}/share", documentId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiresAt").exists());
    }

    @Test
    @DisplayName("Should list active share links for owner")
    void testListShareLinks_Success() throws Exception {
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        DocumentShareListItemResponse item = DocumentShareListItemResponse.builder()
                .shareLinkId(UUID.randomUUID())
                .token("public-token")
                .type(com.docusphere.backend.documentShare.entity.ShareLinkType.PUBLIC)
                .permission(DocumentSharePermission.VIEW)
                .expiresAt(LocalDateTime.now().plusDays(1))
                .createdAt(LocalDateTime.now())
                .build();

        when(documentSharingService.listActiveShareLinks(userId, documentId))
                .thenReturn(List.of(item));

        mockMvc.perform(get("/api/documents/{id}/shares", documentId)
                        .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].token").value("public-token"))
                .andExpect(jsonPath("$.data[0].type").value("PUBLIC"));
    }

    // ==================== REVOKE SHARE LINK TESTS ====================

    @Test
    @DisplayName("Should successfully revoke share link")
    void testRevokeShareLink_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        String shareToken = "token-to-revoke";

        doNothing().when(documentSharingService).revokeShareLink(userId, documentId, shareToken);

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{id}/share", documentId)
                .header("Authorization", authToken)
                .param("token", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Share link revoked successfully"));

        verify(documentSharingService, times(1)).revokeShareLink(userId, documentId, shareToken);
    }

    @Test
    @DisplayName("Should throw exception when share token not found")
    void testRevokeShareLink_TokenNotFound_Returns404() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        String shareToken = "non-existent-token";

        doThrow(new DocumentNotFoundException("Share link not found"))
                .when(documentSharingService).revokeShareLink(userId, documentId, shareToken);

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{id}/share", documentId)
                .header("Authorization", authToken)
                .param("token", shareToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should throw exception when revoking without authorization")
    void testRevokeShareLink_NotOwner_Unauthorized() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        String shareToken = "valid-token";

        doThrow(new UnauthorizedAccessException("Only the owner can perform this action"))
                .when(documentSharingService).revokeShareLink(userId, documentId, shareToken);

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{id}/share", documentId)
                .header("Authorization", authToken)
                .param("token", shareToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should require token parameter for revoke")
    void testRevokeShareLink_MissingToken_BadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/documents/{id}/share", documentId)
                .header("Authorization", authToken))
                .andExpect(status().isBadRequest());
    }

    // ==================== OPEN SHARED DOCUMENT TESTS ====================

    @Test
    @DisplayName("Should successfully open shared document")
    void testOpenSharedDocument_Success() throws Exception {
        // Arrange
        String shareToken = "valid-share-token";

        SharedDocumentResponse response = SharedDocumentResponse.builder()
                .documentId(documentId)
                .name("Shared Document")
                .type("pdf")
                .sizeBytes(1024L)
                .fileUrl("http://example.com/file")
                .permission(DocumentSharePermission.VIEW)
                .canComment(false)
                .build();

        when(documentSharingService.openSharedDocument(shareToken))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/share/{token}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Shared Document"))
                .andExpect(jsonPath("$.data.permission").value("VIEW"))
                .andExpect(jsonPath("$.data.canComment").value(false));

        verify(documentSharingService, times(1)).openSharedDocument(shareToken);
    }

    @Test
    @DisplayName("Should return shared document with comment permission")
    void testOpenSharedDocument_WithCommentPermission_Success() throws Exception {
        // Arrange
        String shareToken = "comment-token";

        SharedDocumentResponse response = SharedDocumentResponse.builder()
                .documentId(documentId)
                .name("Shared Document")
                .type("pdf")
                .sizeBytes(1024L)
                .fileUrl("http://example.com/file")
                .permission(DocumentSharePermission.COMMENT)
                .canComment(true)
                .build();

        when(documentSharingService.openSharedDocument(shareToken))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/share/{token}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canComment").value(true));
    }

    @Test
    @DisplayName("Should throw exception when share token invalid")
    void testOpenSharedDocument_InvalidToken_Returns404() throws Exception {
        // Arrange
        String invalidToken = "invalid-token";

        when(documentSharingService.openSharedDocument(invalidToken))
                .thenThrow(new DocumentNotFoundException("Share link not found"));

        // Act & Assert
        mockMvc.perform(get("/api/share/{token}", invalidToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should throw exception when share link revoked")
    void testOpenSharedDocument_RevokedLink_Unauthorized() throws Exception {
        // Arrange
        String revokedToken = "revoked-token";

        when(documentSharingService.openSharedDocument(revokedToken))
                .thenThrow(new UnauthorizedAccessException("This link is no longer valid"));

        // Act & Assert
        mockMvc.perform(get("/api/share/{token}", revokedToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should throw exception when share link expired")
    void testOpenSharedDocument_ExpiredLink_Unauthorized() throws Exception {
        // Arrange
        String expiredToken = "expired-token";

        when(documentSharingService.openSharedDocument(expiredToken))
                .thenThrow(new UnauthorizedAccessException("Share link expired"));

        // Act & Assert
        mockMvc.perform(get("/api/share/{token}", expiredToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should include complete document details in response")
    void testOpenSharedDocument_CompleteDetails_Success() throws Exception {
        // Arrange
        String shareToken = "complete-token";

        SharedDocumentResponse response = SharedDocumentResponse.builder()
                .documentId(documentId)
                .name("Complete Document.pdf")
                .type("pdf")
                .sizeBytes(5120L)
                .fileUrl("http://example.com/complete-file")
                .permission(DocumentSharePermission.VIEW)
                .canComment(false)
                .build();

        when(documentSharingService.openSharedDocument(shareToken))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/share/{token}", shareToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.data.name").value("Complete Document.pdf"))
                .andExpect(jsonPath("$.data.type").value("pdf"))
                .andExpect(jsonPath("$.data.sizeBytes").value(5120))
                .andExpect(jsonPath("$.data.fileUrl").exists());
    }

    @Test
    @DisplayName("Should handle multiple share tokens independently")
    void testOpenSharedDocument_MultipleTokens_Success() throws Exception {
        // Arrange
        String token1 = "token-1";
        String token2 = "token-2";

        SharedDocumentResponse response1 = SharedDocumentResponse.builder()
                .documentId(UUID.randomUUID())
                .name("Document 1")
                .type("pdf")
                .sizeBytes(1024L)
                .fileUrl("http://example.com/file1")
                .permission(DocumentSharePermission.VIEW)
                .canComment(false)
                .build();

        SharedDocumentResponse response2 = SharedDocumentResponse.builder()
                .documentId(UUID.randomUUID())
                .name("Document 2")
                .type("docx")
                .sizeBytes(2048L)
                .fileUrl("http://example.com/file2")
                .permission(DocumentSharePermission.COMMENT)
                .canComment(true)
                .build();

        when(documentSharingService.openSharedDocument(token1)).thenReturn(response1);
        when(documentSharingService.openSharedDocument(token2)).thenReturn(response2);

        // Act & Assert
        mockMvc.perform(get("/api/share/{token}", token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Document 1"));

        mockMvc.perform(get("/api/share/{token}", token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Document 2"));

        verify(documentSharingService).openSharedDocument(token1);
        verify(documentSharingService).openSharedDocument(token2);
    }

    @Test
    @DisplayName("Should validate document ID UUID format")
    void testCreateShareLink_InvalidDocumentId_BadRequest() throws Exception {
        // Act & Assert
        CreateShareLinkRequest request = new CreateShareLinkRequest();
        request.setType(ShareLinkType.PUBLIC);
        request.setPermission(DocumentSharePermission.VIEW);

        mockMvc.perform(post("/api/documents/invalid-uuid/share")
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }
}










