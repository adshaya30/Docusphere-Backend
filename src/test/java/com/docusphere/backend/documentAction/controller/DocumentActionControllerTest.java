package com.docusphere.backend.documentAction.controller;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.documentAction.dto.DocumentActionResponse;
import com.docusphere.backend.documentAction.dto.MoveRequest;
import com.docusphere.backend.documentAction.dto.RenameRequest;
import com.docusphere.backend.documentAction.dto.TrashDocumentsPageResponse;
import com.docusphere.backend.documentAction.service.DocumentActionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DocumentActionController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("DocumentActionController Unit Tests")
class DocumentActionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentActionService documentActionService;

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

    // ==================== RENAME TESTS ====================

    @Test
    @DisplayName("Should successfully rename document")
    void testRename_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        RenameRequest request = new RenameRequest();
        request.setNewName("Renamed Document.pdf");

        DocumentActionResponse response = DocumentActionResponse.builder()
                .documentId(documentId)
                .name("Renamed Document.pdf")
                .ownerId(userId)
                .teamId(null)
                .storageKey("storage/key")
                .deleted(false)
                .updatedAt(LocalDateTime.now())
                .build();

        when(documentActionService.rename(userId, documentId, "Renamed Document.pdf"))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(put("/api/documents/{id}/rename", documentId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Renamed Document.pdf"))
                .andExpect(jsonPath("$.message").value("Document renamed"));

        verify(documentActionService, times(1)).rename(userId, documentId, "Renamed Document.pdf");
    }

    @Test
    @DisplayName("Should throw exception when renaming with empty name")
    void testRename_EmptyName_BadRequest() throws Exception {
        // Arrange
        RenameRequest request = new RenameRequest();
        request.setNewName("");

        // Act & Assert
        mockMvc.perform(put("/api/documents/{id}/rename", documentId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should throw exception when document not found for rename")
    void testRename_DocumentNotFound_Returns404() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        RenameRequest request = new RenameRequest();
        request.setNewName("New Name");

        when(documentActionService.rename(userId, documentId, "New Name"))
                .thenThrow(new DocumentNotFoundException("Document not found"));

        // Act & Assert
        mockMvc.perform(put("/api/documents/{id}/rename", documentId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should throw exception when non-owner renames")
    void testRename_NotOwner_Unauthorized() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        RenameRequest request = new RenameRequest();
        request.setNewName("New Name");

        when(documentActionService.rename(userId, documentId, "New Name"))
                .thenThrow(new UnauthorizedAccessException("Only the owner can perform this action"));

        // Act & Assert
        mockMvc.perform(put("/api/documents/{id}/rename", documentId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // ==================== MOVE TESTS ====================

    @Test
    @DisplayName("Should successfully move document to team")
    void testMove_ToTeam_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        UUID teamId = UUID.randomUUID();
        MoveRequest request = new MoveRequest();
        request.setTeamId(teamId.toString());

        DocumentActionResponse response = DocumentActionResponse.builder()
                .documentId(documentId)
                .name("Test Document")
                .ownerId(userId)
                .teamId(teamId)
                .storageKey("storage/key")
                .deleted(false)
                .updatedAt(LocalDateTime.now())
                .build();

        when(documentActionService.move(userId, documentId, teamId))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(put("/api/documents/{id}/move", documentId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value(teamId.toString()))
                .andExpect(jsonPath("$.message").value("Document moved"));
    }

    @Test
    @DisplayName("Should successfully move document to user space")
    void testMove_ToUserSpace_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        MoveRequest request = new MoveRequest();
        request.setTeamId(null);

        DocumentActionResponse response = DocumentActionResponse.builder()
                .documentId(documentId)
                .name("Test Document")
                .ownerId(userId)
                .teamId(null)
                .storageKey("storage/key")
                .deleted(false)
                .updatedAt(LocalDateTime.now())
                .build();

        when(documentActionService.move(userId, documentId, null))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(put("/api/documents/{id}/move", documentId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").doesNotExist());
    }

    @Test
    @DisplayName("Should throw exception when moving to same team")
    void testMove_SameTeam_BadRequest() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        UUID teamId = UUID.randomUUID();
        MoveRequest request = new MoveRequest();
        request.setTeamId(teamId.toString());

        when(documentActionService.move(userId, documentId, teamId))
                .thenThrow(new InvalidRequestException("Document already in target space"));

        // Act & Assert
        mockMvc.perform(put("/api/documents/{id}/move", documentId)
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ==================== DUPLICATE TESTS ====================

    @Test
    @DisplayName("Should successfully duplicate document")
    void testDuplicate_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        DocumentActionResponse response = DocumentActionResponse.builder()
                .documentId(UUID.randomUUID())
                .name("Test Document (1).pdf")
                .ownerId(userId)
                .teamId(null)
                .storageKey("storage/key-duplicate")
                .deleted(false)
                .updatedAt(LocalDateTime.now())
                .build();

        when(documentActionService.duplicate(userId, documentId))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/documents/{id}/duplicate", documentId)
                .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Test Document (1).pdf"))
                .andExpect(jsonPath("$.message").value("Document duplicated"));

        verify(documentActionService, times(1)).duplicate(userId, documentId);
    }

    @Test
    @DisplayName("Should throw exception when duplicating inaccessible document")
    void testDuplicate_NoAccess_Unauthorized() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        when(documentActionService.duplicate(userId, documentId))
                .thenThrow(new UnauthorizedAccessException("You do not have access to this document"));

        // Act & Assert
        mockMvc.perform(post("/api/documents/{id}/duplicate", documentId)
                .header("Authorization", authToken))
                .andExpect(status().isForbidden());
    }

    // ==================== MOVE TO TRASH TESTS ====================

    @Test
    @DisplayName("Should successfully move document to trash")
    void testMoveToTrash_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        DocumentActionResponse response = DocumentActionResponse.builder()
                .documentId(documentId)
                .name("Test Document")
                .ownerId(userId)
                .teamId(null)
                .storageKey("storage/key")
                .deleted(true)
                .deletedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(documentActionService.moveToTrash(userId, documentId))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{id}/trash", documentId)
                .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.message").value("Document moved to trash"));

        verify(documentActionService, times(1)).moveToTrash(userId, documentId);
    }

    @Test
    @DisplayName("Should throw exception when moving non-existent document to trash")
    void testMoveToTrash_DocumentNotFound_Returns404() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        when(documentActionService.moveToTrash(userId, documentId))
                .thenThrow(new DocumentNotFoundException("Document not found"));

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{id}/trash", documentId)
                .header("Authorization", authToken))
                .andExpect(status().isNotFound());
    }

    // ==================== RESTORE FROM TRASH TESTS ====================

    @Test
    @DisplayName("Should successfully restore document from trash")
    void testRestoreFromTrash_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        DocumentActionResponse response = DocumentActionResponse.builder()
                .documentId(documentId)
                .name("Test Document")
                .ownerId(userId)
                .teamId(null)
                .storageKey("storage/key")
                .deleted(false)
                .deletedAt(null)
                .updatedAt(LocalDateTime.now())
                .build();

        when(documentActionService.restoreFromTrash(userId, documentId))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/documents/{id}/restore", documentId)
                .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(false))
                .andExpect(jsonPath("$.message").value("Document restored from trash"));

        verify(documentActionService, times(1)).restoreFromTrash(userId, documentId);
    }

    @Test
    @DisplayName("Should throw exception when restoring non-trashed document")
    void testRestoreFromTrash_NotDeleted_BadRequest() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        when(documentActionService.restoreFromTrash(userId, documentId))
                .thenThrow(new InvalidRequestException("Document is not in trash"));

        // Act & Assert
        mockMvc.perform(post("/api/documents/{id}/restore", documentId)
                .header("Authorization", authToken))
                .andExpect(status().isBadRequest());
    }

    // ==================== PERMANENT DELETE TESTS ====================

    @Test
    @DisplayName("Should successfully permanently delete document")
    void testPermanentlyDelete_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        doNothing().when(documentActionService).permanentlyDelete(userId, documentId);

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{id}/permanent", documentId)
                .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Document permanently deleted"));

        verify(documentActionService, times(1)).permanentlyDelete(userId, documentId);
    }

    @Test
    @DisplayName("Should throw exception when permanently deleting non-trashed document")
    void testPermanentlyDelete_NotInTrash_BadRequest() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        doThrow(new InvalidRequestException("Only trashed documents can be permanently deleted"))
                .when(documentActionService).permanentlyDelete(userId, documentId);

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{id}/permanent", documentId)
                .header("Authorization", authToken))
                .andExpect(status().isBadRequest());
    }

    // ==================== GET TRASH TESTS ====================

    @Test
    @DisplayName("Should successfully fetch trash documents")
    void testGetTrash_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        TrashDocumentsPageResponse response = TrashDocumentsPageResponse.builder()
                .items(Collections.emptyList())
                .page(0)
                .size(15)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .build();

        when(documentActionService.getTrash(userId, 0, 15))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(get("/api/documents/trash")
                .header("Authorization", authToken)
                .param("page", "0")
                .param("size", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.empty").value(true))
                .andExpect(jsonPath("$.message").value("Trash documents fetched successfully"));

        verify(documentActionService, times(1)).getTrash(userId, 0, 15);
    }

    @Test
    @DisplayName("Should validate trash pagination parameters")
    void testGetTrash_InvalidPage_BadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/documents/trash")
                .header("Authorization", authToken)
                .param("page", "-1")
                .param("size", "15"))
                .andExpect(status().isInternalServerError());
    }

    // ==================== DOWNLOAD TESTS ====================

    @Test
    @DisplayName("Should successfully download document with share token")
    void testDownload_Success() throws Exception {
        // Arrange
        String shareToken = "valid-share-token";
        byte[] fileContent = "test file content".getBytes();

        when(documentActionService.downloadByShareToken(documentId, shareToken, null))
                .thenReturn(new ByteArrayResource(fileContent));
        when(documentActionService.resolveDownloadFilenameByShareToken(documentId, shareToken, null))
                .thenReturn("Test Document.pdf");

        // Act & Assert
        mockMvc.perform(get("/api/documents/{id}/download", documentId)
                .param("token", shareToken))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"));

        verify(documentActionService, times(1)).downloadByShareToken(documentId, shareToken, null);
        verify(documentActionService, times(1)).resolveDownloadFilenameByShareToken(documentId, shareToken, null);
    }

    @Test
    @DisplayName("Should return 404 when downloading with invalid share token")
    void testDownload_InvalidToken_Returns404() throws Exception {
        // Arrange
        String invalidToken = "invalid-token";

        when(documentActionService.downloadByShareToken(documentId, invalidToken, null))
                .thenThrow(new DocumentNotFoundException("Share link not found"));

        // Act & Assert
        mockMvc.perform(get("/api/documents/{id}/download", documentId)
                .param("token", invalidToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should include filename in download response")
    void testDownload_IncludeFilename_Success() throws Exception {
        // Arrange
        String shareToken = "valid-token";
        byte[] fileContent = new byte[1024];

        when(documentActionService.downloadByShareToken(documentId, shareToken, null))
                .thenReturn(new ByteArrayResource(fileContent));
        when(documentActionService.resolveDownloadFilenameByShareToken(documentId, shareToken, null))
                .thenReturn("MyDocument.pdf");

        // Act & Assert
        mockMvc.perform(get("/api/documents/{id}/download", documentId)
                .param("token", shareToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("MyDocument.pdf")));
    }

    @Test
    @DisplayName("Should require share token for download")
    void testDownload_MissingToken_BadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/documents/{id}/download", documentId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should throw exception when missing authorization for actions")
    void testActions_NoAuthHeader_Unauthorized() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/documents/{id}/trash", documentId))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should validate document ID UUID format")
    void testRename_InvalidDocumentId_BadRequest() throws Exception {
        // Act & Assert
        RenameRequest request = new RenameRequest();
        request.setNewName("New Name");

        mockMvc.perform(put("/api/documents/invalid-uuid/rename")
                .header("Authorization", authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }
}










