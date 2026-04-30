package com.docusphere.backend.documentService;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.service.DocumentService;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.documentStar.dto.DocumentStarResponse;
import com.docusphere.backend.documentStar.service.DocumentStarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentService Unit Tests")
class DocumentServiceTest {

    @Mock
    private DocumentStarService documentStarService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FileStorageService fileStorageService;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentStarService, documentRepository, fileStorageService);
    }

    // ==================== STAR TESTS ====================

    @Test
    @DisplayName("Should successfully star a document")
    void testStar_Success() {
        // Arrange
        String userId = "123";
        String documentId = "550e8400-e29b-41d4-a716-446655440000";
        LocalDateTime now = LocalDateTime.now();

        DocumentStarResponse expectedResponse = DocumentStarResponse.builder()
                .userId(123L)
                .documentId(UUID.fromString(documentId))
                .starred(true)
                .starredAt(now)
                .build();

        when(documentStarService.star(123L, UUID.fromString(documentId)))
                .thenReturn(expectedResponse);

        // Act
        DocumentStarResponse response = documentService.star(userId, documentId);

        // Assert
        assertNotNull(response);
        assertTrue(response.isStarred());
        assertEquals(123L, response.getUserId());
        assertEquals(UUID.fromString(documentId), response.getDocumentId());
        assertEquals(now, response.getStarredAt());
        verify(documentStarService, times(1)).star(123L, UUID.fromString(documentId));
    }

    @Test
    @DisplayName("Should throw exception when userId is null")
    void testStar_NullUserId_ThrowsException() {
        // Arrange
        String documentId = "550e8400-e29b-41d4-a716-446655440000";

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentService.star(null, documentId)
        );
        assertEquals("userId is required", exception.getMessage());
        verify(documentStarService, never()).star(anyLong(), any());
    }

    @Test
    @DisplayName("Should throw exception when userId is blank")
    void testStar_BlankUserId_ThrowsException() {
        // Arrange
        String documentId = "550e8400-e29b-41d4-a716-446655440000";

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentService.star("   ", documentId)
        );
        assertEquals("userId is required", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when userId is not a valid number")
    void testStar_InvalidUserId_ThrowsException() {
        // Arrange
        String documentId = "550e8400-e29b-41d4-a716-446655440000";

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentService.star("not-a-number", documentId)
        );
        assertEquals("userId must be a valid numeric id", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when documentId is null")
    void testStar_NullDocumentId_ThrowsException() {
        // Arrange
        String userId = "123";

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentService.star(userId, null)
        );
        assertEquals("documentId is required", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when documentId is blank")
    void testStar_BlankDocumentId_ThrowsException() {
        // Arrange
        String userId = "123";

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentService.star(userId, "   ")
        );
        assertEquals("documentId is required", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when documentId is not a valid UUID")
    void testStar_InvalidDocumentId_ThrowsException() {
        // Arrange
        String userId = "123";

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentService.star(userId, "not-a-uuid")
        );
        assertEquals("documentId must be a valid UUID", exception.getMessage());
    }

    // ==================== UNSTAR TESTS ====================

    @Test
    @DisplayName("Should successfully unstar a document")
    void testUnstar_Success() {
        // Arrange
        String userId = "123";
        String documentId = "550e8400-e29b-41d4-a716-446655440000";

        DocumentStarResponse expectedResponse = DocumentStarResponse.builder()
                .userId(123L)
                .documentId(UUID.fromString(documentId))
                .starred(false)
                .starredAt(null)
                .build();

        when(documentStarService.unstar(123L, UUID.fromString(documentId)))
                .thenReturn(expectedResponse);

        // Act
        DocumentStarResponse response = documentService.unstar(userId, documentId);

        // Assert
        assertNotNull(response);
        assertFalse(response.isStarred());
        assertEquals(123L, response.getUserId());
        assertNull(response.getStarredAt());
        verify(documentStarService, times(1)).unstar(123L, UUID.fromString(documentId));
    }

    @Test
    @DisplayName("Should throw exception when unstaring with null userId")
    void testUnstar_NullUserId_ThrowsException() {
        // Arrange
        String documentId = "550e8400-e29b-41d4-a716-446655440000";

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentService.unstar(null, documentId)
        );
        assertEquals("userId is required", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when unstaring with null documentId")
    void testUnstar_NullDocumentId_ThrowsException() {
        // Arrange
        String userId = "123";

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentService.unstar(userId, null)
        );
        assertEquals("documentId is required", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when unstaring with invalid UUID format")
    void testUnstar_InvalidDocumentIdFormat_ThrowsException() {
        // Arrange
        String userId = "123";

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentService.unstar(userId, "invalid-uuid-format")
        );
        assertEquals("documentId must be a valid UUID", exception.getMessage());
    }

    @Test
    @DisplayName("Should handle large userId values")
    void testStar_LargeUserId_Success() {
        // Arrange
        String userId = "9223372036854775807"; // Long.MAX_VALUE
        String documentId = "550e8400-e29b-41d4-a716-446655440000";

        DocumentStarResponse expectedResponse = DocumentStarResponse.builder()
                .userId(Long.MAX_VALUE)
                .documentId(UUID.fromString(documentId))
                .starred(true)
                .starredAt(LocalDateTime.now())
                .build();

        when(documentStarService.star(Long.MAX_VALUE, UUID.fromString(documentId)))
                .thenReturn(expectedResponse);

        // Act
        DocumentStarResponse response = documentService.star(userId, documentId);

        // Assert
        assertNotNull(response);
        assertEquals(Long.MAX_VALUE, response.getUserId());
    }

    @Test
    @DisplayName("Should trim whitespace from userId")
    void testStar_UserIdWithWhitespace_Success() {
        // Arrange
        String userId = "  123  ";
        String documentId = "550e8400-e29b-41d4-a716-446655440000";

        DocumentStarResponse expectedResponse = DocumentStarResponse.builder()
                .userId(123L)
                .documentId(UUID.fromString(documentId))
                .starred(true)
                .starredAt(LocalDateTime.now())
                .build();

        when(documentStarService.star(123L, UUID.fromString(documentId)))
                .thenReturn(expectedResponse);

        // Act
        DocumentStarResponse response = documentService.star(userId, documentId);

        // Assert
        assertNotNull(response);
        assertEquals(123L, response.getUserId());
    }

    @Test
    @DisplayName("Should trim whitespace from documentId UUID")
    void testStar_DocumentIdWithWhitespace_Success() {
        // Arrange
        String userId = "123";
        String documentId = "  550e8400-e29b-41d4-a716-446655440000  ";

        DocumentStarResponse expectedResponse = DocumentStarResponse.builder()
                .userId(123L)
                .documentId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
                .starred(true)
                .starredAt(LocalDateTime.now())
                .build();

        when(documentStarService.star(123L, UUID.fromString("550e8400-e29b-41d4-a716-446655440000")))
                .thenReturn(expectedResponse);

        // Act
        DocumentStarResponse response = documentService.star(userId, documentId);

        // Assert
        assertNotNull(response);
    }
}

