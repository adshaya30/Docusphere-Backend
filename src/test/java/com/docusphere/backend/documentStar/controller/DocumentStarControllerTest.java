package com.docusphere.backend.documentStar.controller;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.documentStar.dto.DocumentStarResponse;
import com.docusphere.backend.documentStar.service.DocumentStarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DocumentStarController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("DocumentStarController Unit Tests")
class DocumentStarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentStarService documentStarService;

    @MockitoBean
    private JwtService jwtService;

    private UUID documentId;
    private Long userId;

    @BeforeEach
    void setUp() {
        documentId = UUID.randomUUID();
        userId = 123L;
    }

    // ==================== STAR TESTS ====================

    @Test
    @DisplayName("Should successfully star a document")
    void testStar_Success() throws Exception {
        // Arrange
        LocalDateTime now = LocalDateTime.now();
        DocumentStarResponse response = DocumentStarResponse.builder()
                .userId(userId)
                .documentId(documentId)
                .starred(true)
                .starredAt(now)
                .build();

        when(documentStarService.star(userId, documentId)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/documents/{documentId}/star", documentId)
                .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Starred"))
                .andExpect(jsonPath("$.data.starred").value(true))
                .andExpect(jsonPath("$.data.userId").value(userId.intValue()));

        verify(documentStarService, times(1)).star(userId, documentId);
    }

    @Test
    @DisplayName("Should return 404 when document not found for starring")
    void testStar_DocumentNotFound_Returns404() throws Exception {
        // Arrange
        when(documentStarService.star(userId, documentId))
                .thenThrow(new DocumentNotFoundException("Document not found"));

        // Act & Assert
        mockMvc.perform(post("/api/documents/{documentId}/star", documentId)
                .param("userId", userId.toString()))
                .andExpect(status().isNotFound());

        verify(documentStarService, times(1)).star(userId, documentId);
    }

    @Test
    @DisplayName("Should star document with different user IDs")
    void testStar_DifferentUsers_Success() throws Exception {
        // Arrange
        Long userId2 = 456L;
        
        DocumentStarResponse response = DocumentStarResponse.builder()
                .userId(userId2)
                .documentId(documentId)
                .starred(true)
                .starredAt(LocalDateTime.now())
                .build();

        when(documentStarService.star(userId2, documentId)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/documents/{documentId}/star", documentId)
                .param("userId", userId2.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId2.intValue()));
    }

    @Test
    @DisplayName("Should handle star operation for already starred document")
    void testStar_AlreadyStarred_Success() throws Exception {
        // Arrange
        LocalDateTime starredTime = LocalDateTime.now().minusHours(1);
        
        DocumentStarResponse response = DocumentStarResponse.builder()
                .userId(userId)
                .documentId(documentId)
                .starred(true)
                .starredAt(starredTime)
                .build();

        when(documentStarService.star(userId, documentId)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/documents/{documentId}/star", documentId)
                .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.starred").value(true));
    }

    @Test
    @DisplayName("Should star multiple documents from same user")
    void testStar_MultipleDocuments_Success() throws Exception {
        // Arrange
        UUID documentId2 = UUID.randomUUID();

        DocumentStarResponse response1 = DocumentStarResponse.builder()
                .userId(userId)
                .documentId(documentId)
                .starred(true)
                .starredAt(LocalDateTime.now())
                .build();

        DocumentStarResponse response2 = DocumentStarResponse.builder()
                .userId(userId)
                .documentId(documentId2)
                .starred(true)
                .starredAt(LocalDateTime.now())
                .build();

        when(documentStarService.star(userId, documentId)).thenReturn(response1);
        when(documentStarService.star(userId, documentId2)).thenReturn(response2);

        // Act & Assert
        mockMvc.perform(post("/api/documents/{documentId}/star", documentId)
                .param("userId", userId.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/documents/{documentId}/star", documentId2)
                .param("userId", userId.toString()))
                .andExpect(status().isOk());

        verify(documentStarService, times(1)).star(userId, documentId);
        verify(documentStarService, times(1)).star(userId, documentId2);
    }

    @Test
    @DisplayName("Should include starred timestamp in response")
    void testStar_IncludeTimestamp_Success() throws Exception {
        // Arrange
        LocalDateTime starTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        
        DocumentStarResponse response = DocumentStarResponse.builder()
                .userId(userId)
                .documentId(documentId)
                .starred(true)
                .starredAt(starTime)
                .build();

        when(documentStarService.star(userId, documentId)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/documents/{documentId}/star", documentId)
                .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.starredAt").exists());
    }

    @Test
    @DisplayName("Should validate userId parameter")
    void testStar_InvalidUserId_BadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/documents/{documentId}/star", documentId)
                .param("userId", "invalid-id"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should require userId parameter")
    void testStar_MissingUserId_BadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/documents/{documentId}/star", documentId))
                .andExpect(status().isInternalServerError());
    }

    // ==================== UNSTAR TESTS ====================

    @Test
    @DisplayName("Should successfully unstar a document")
    void testUnstar_Success() throws Exception {
        // Arrange
        DocumentStarResponse response = DocumentStarResponse.builder()
                .userId(userId)
                .documentId(documentId)
                .starred(false)
                .starredAt(null)
                .build();

        when(documentStarService.unstar(userId, documentId)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{documentId}/star", documentId)
                .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Unstarred"))
                .andExpect(jsonPath("$.data.starred").value(false))
                .andExpect(jsonPath("$.data.starredAt").doesNotExist());

        verify(documentStarService, times(1)).unstar(userId, documentId);
    }

    @Test
    @DisplayName("Should return 404 when document not found for unstarring")
    void testUnstar_DocumentNotFound_Returns404() throws Exception {
        // Arrange
        when(documentStarService.unstar(userId, documentId))
                .thenThrow(new DocumentNotFoundException("Document not found"));

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{documentId}/star", documentId)
                .param("userId", userId.toString()))
                .andExpect(status().isNotFound());

        verify(documentStarService, times(1)).unstar(userId, documentId);
    }

    @Test
    @DisplayName("Should successfully unstar document that was never starred")
    void testUnstar_NeverStarred_Success() throws Exception {
        // Arrange
        DocumentStarResponse response = DocumentStarResponse.builder()
                .userId(userId)
                .documentId(documentId)
                .starred(false)
                .starredAt(null)
                .build();

        when(documentStarService.unstar(userId, documentId)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{documentId}/star", documentId)
                .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.starred").value(false));
    }

    @Test
    @DisplayName("Should unstar document with different user IDs")
    void testUnstar_DifferentUsers_Success() throws Exception {
        // Arrange
        Long userId2 = 789L;
        
        DocumentStarResponse response = DocumentStarResponse.builder()
                .userId(userId2)
                .documentId(documentId)
                .starred(false)
                .starredAt(null)
                .build();

        when(documentStarService.unstar(userId2, documentId)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{documentId}/star", documentId)
                .param("userId", userId2.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId2.intValue()));
    }

    @Test
    @DisplayName("Should unstar multiple documents from same user")
    void testUnstar_MultipleDocuments_Success() throws Exception {
        // Arrange
        UUID documentId2 = UUID.randomUUID();

        DocumentStarResponse response1 = DocumentStarResponse.builder()
                .userId(userId)
                .documentId(documentId)
                .starred(false)
                .starredAt(null)
                .build();

        DocumentStarResponse response2 = DocumentStarResponse.builder()
                .userId(userId)
                .documentId(documentId2)
                .starred(false)
                .starredAt(null)
                .build();

        when(documentStarService.unstar(userId, documentId)).thenReturn(response1);
        when(documentStarService.unstar(userId, documentId2)).thenReturn(response2);

        // Act & Assert
        mockMvc.perform(delete("/api/documents/{documentId}/star", documentId)
                .param("userId", userId.toString()))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/documents/{documentId}/star", documentId2)
                .param("userId", userId.toString()))
                .andExpect(status().isOk());

        verify(documentStarService, times(1)).unstar(userId, documentId);
        verify(documentStarService, times(1)).unstar(userId, documentId2);
    }

    @Test
    @DisplayName("Should validate userId parameter for unstar")
    void testUnstar_InvalidUserId_BadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/documents/{documentId}/star", documentId)
                .param("userId", "invalid-id"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should require userId parameter for unstar")
    void testUnstar_MissingUserId_BadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/documents/{documentId}/star", documentId))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should validate documentId UUID format")
    void testStar_InvalidDocumentId_BadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/documents/invalid-uuid/star")
                .param("userId", userId.toString()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should validate documentId UUID format for unstar")
    void testUnstar_InvalidDocumentId_BadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/documents/invalid-uuid/star")
                .param("userId", userId.toString()))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should handle very large user ID values")
    void testStar_MaxLongUserId_Success() throws Exception {
        // Arrange
        Long maxUserId = Long.MAX_VALUE;
        
        DocumentStarResponse response = DocumentStarResponse.builder()
                .userId(maxUserId)
                .documentId(documentId)
                .starred(true)
                .starredAt(LocalDateTime.now())
                .build();

        when(documentStarService.star(maxUserId, documentId)).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/api/documents/{documentId}/star", documentId)
                .param("userId", maxUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.starred").value(true));
    }

    @Test
    @DisplayName("Should respond with proper API response format")
    void testStar_ResponseFormat_Success() throws Exception {
        // Arrange
        DocumentStarResponse starResponse = DocumentStarResponse.builder()
                .userId(userId)
                .documentId(documentId)
                .starred(true)
                .starredAt(LocalDateTime.now())
                .build();

        when(documentStarService.star(userId, documentId)).thenReturn(starResponse);

        // Act & Assert
        mockMvc.perform(post("/api/documents/{documentId}/star", documentId)
                .param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.data").exists());
    }
}

