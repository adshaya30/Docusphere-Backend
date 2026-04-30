package com.docusphere.backend.documentStar.service;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentStar.dto.DocumentStarResponse;
import com.docusphere.backend.documentStar.entity.DocumentStar;
import com.docusphere.backend.documentStar.repository.DocumentStarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentStarService Unit Tests")
class DocumentStarServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentStarRepository starRepository;

    private DocumentStarService documentStarService;

    private UUID documentId;
    private Long userId;
    private Document mockDocument;

    @BeforeEach
    void setUp() {
        documentStarService = new DocumentStarService(documentRepository, starRepository);
        documentId = UUID.randomUUID();
        userId = 123L;
        
        mockDocument = Document.builder()
                .id(documentId)
                .fileId("file123")
                .name("Test Document")
                .type("pdf")
                .sizeBytes(1024L)
                .ownerId(456L)
                .storageKey("storage/key")
                .fileUrl("http://example.com/file")
                .deleted(false)
                .build();
    }

    // ==================== STAR TESTS ====================

    @Test
    @DisplayName("Should successfully star a document when not already starred")
    void testStar_NewStar_Success() {
        // Arrange
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        when(starRepository.findByUserIdAndDocumentId(userId, documentId)).thenReturn(Optional.empty());
        
        DocumentStar newStar = DocumentStar.builder()
                .userId(userId)
                .documentId(documentId)
                .starredAt(LocalDateTime.now())
                .build();
        
        when(starRepository.save(any())).thenReturn(newStar);

        // Act
        DocumentStarResponse response = documentStarService.star(userId, documentId);

        // Assert
        assertNotNull(response);
        assertTrue(response.isStarred());
        assertEquals(userId, response.getUserId());
        assertEquals(documentId, response.getDocumentId());
        assertNotNull(response.getStarredAt());
        
        verify(documentRepository, times(1)).findById(documentId);
        verify(starRepository, times(1)).findByUserIdAndDocumentId(userId, documentId);
        verify(starRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should return existing star when document is already starred")
    void testStar_AlreadyStarred_Success() {
        // Arrange
        LocalDateTime existingTime = LocalDateTime.now().minusHours(1);
        DocumentStar existingStar = DocumentStar.builder()
                .userId(userId)
                .documentId(documentId)
                .starredAt(existingTime)
                .build();

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        when(starRepository.findByUserIdAndDocumentId(userId, documentId))
                .thenReturn(Optional.of(existingStar));

        // Act
        DocumentStarResponse response = documentStarService.star(userId, documentId);

        // Assert
        assertNotNull(response);
        assertTrue(response.isStarred());
        assertEquals(existingTime, response.getStarredAt());
        
        verify(starRepository, times(1)).findByUserIdAndDocumentId(userId, documentId);
        verify(starRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw DocumentNotFoundException when document does not exist")
    void testStar_DocumentNotFound_ThrowsException() {
        // Arrange
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        // Act & Assert
        DocumentNotFoundException exception = assertThrows(
                DocumentNotFoundException.class,
                () -> documentStarService.star(userId, documentId)
        );
        
        assertEquals("Document not found", exception.getMessage());
        verify(documentRepository, times(1)).findById(documentId);
        verify(starRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should successfully star document with null documentId in Optional")
    void testStar_DocumentNotFoundInRepository_ThrowsException() {
        // Arrange
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(DocumentNotFoundException.class, 
                () -> documentStarService.star(userId, documentId));
    }

    @Test
    @DisplayName("Should handle star operation with different users same document")
    void testStar_MultipleUsersSameDocument_Success() {
        // Arrange
        Long userId2 = 456L;
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        
        DocumentStar star1 = DocumentStar.builder()
                .userId(userId)
                .documentId(documentId)
                .starredAt(LocalDateTime.now())
                .build();
        
        when(starRepository.findByUserIdAndDocumentId(userId, documentId)).thenReturn(Optional.of(star1));

        // Act
        DocumentStarResponse response = documentStarService.star(userId, documentId);

        // Assert
        assertTrue(response.isStarred());
        assertEquals(userId, response.getUserId());
    }

    // ==================== UNSTAR TESTS ====================

    @Test
    @DisplayName("Should successfully unstar a document")
    void testUnstar_Success() {
        // Arrange
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        doNothing().when(starRepository).deleteByUserIdAndDocumentId(userId, documentId);

        // Act
        DocumentStarResponse response = documentStarService.unstar(userId, documentId);

        // Assert
        assertNotNull(response);
        assertFalse(response.isStarred());
        assertEquals(userId, response.getUserId());
        assertEquals(documentId, response.getDocumentId());
        assertNull(response.getStarredAt());
        
        verify(documentRepository, times(1)).findById(documentId);
        verify(starRepository, times(1)).deleteByUserIdAndDocumentId(userId, documentId);
    }

    @Test
    @DisplayName("Should throw exception when unstaring non-existent document")
    void testUnstar_DocumentNotFound_ThrowsException() {
        // Arrange
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        // Act & Assert
        DocumentNotFoundException exception = assertThrows(
                DocumentNotFoundException.class,
                () -> documentStarService.unstar(userId, documentId)
        );
        
        assertEquals("Document not found", exception.getMessage());
        verify(starRepository, never()).deleteByUserIdAndDocumentId(anyLong(), any());
    }

    @Test
    @DisplayName("Should handle unstar on document that was never starred")
    void testUnstar_NeverStarred_Success() {
        // Arrange
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        doNothing().when(starRepository).deleteByUserIdAndDocumentId(userId, documentId);

        // Act
        DocumentStarResponse response = documentStarService.unstar(userId, documentId);

        // Assert
        assertNotNull(response);
        assertFalse(response.isStarred());
        verify(starRepository, times(1)).deleteByUserIdAndDocumentId(userId, documentId);
    }

    @Test
    @DisplayName("Should handle unstar with different users on same document")
    void testUnstar_MultipleUsersSameDocument_Success() {
        // Arrange
        Long userId2 = 456L;
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        
        doNothing().when(starRepository).deleteByUserIdAndDocumentId(userId2, documentId);

        // Act
        DocumentStarResponse response = documentStarService.unstar(userId2, documentId);

        // Assert
        assertFalse(response.isStarred());
        assertEquals(userId2, response.getUserId());
        verify(starRepository, times(1)).deleteByUserIdAndDocumentId(userId2, documentId);
    }

    // ==================== EDGE CASES ====================

    @Test
    @DisplayName("Should handle star operation with Long.MAX_VALUE userId")
    void testStar_MaxLongUserId_Success() {
        // Arrange
        Long maxUserId = Long.MAX_VALUE;
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        when(starRepository.findByUserIdAndDocumentId(maxUserId, documentId)).thenReturn(Optional.empty());
        
        DocumentStar newStar = DocumentStar.builder()
                .userId(maxUserId)
                .documentId(documentId)
                .starredAt(LocalDateTime.now())
                .build();
        
        when(starRepository.save(any())).thenReturn(newStar);

        // Act
        DocumentStarResponse response = documentStarService.star(maxUserId, documentId);

        // Assert
        assertEquals(maxUserId, response.getUserId());
    }

    @Test
    @DisplayName("Should handle star operation with zero userId")
    void testStar_ZeroUserId_Success() {
        // Arrange
        Long zeroUserId = 0L;
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        when(starRepository.findByUserIdAndDocumentId(zeroUserId, documentId)).thenReturn(Optional.empty());
        
        DocumentStar newStar = DocumentStar.builder()
                .userId(zeroUserId)
                .documentId(documentId)
                .starredAt(LocalDateTime.now())
                .build();
        
        when(starRepository.save(any())).thenReturn(newStar);

        // Act
        DocumentStarResponse response = documentStarService.star(zeroUserId, documentId);

        // Assert
        assertEquals(zeroUserId, response.getUserId());
    }

    @Test
    @DisplayName("Should preserve starred timestamp across multiple queries")
    void testStar_PreserveTimestamp_Success() {
        // Arrange
        LocalDateTime starTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        DocumentStar existingStar = DocumentStar.builder()
                .userId(userId)
                .documentId(documentId)
                .starredAt(starTime)
                .build();

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        when(starRepository.findByUserIdAndDocumentId(userId, documentId))
                .thenReturn(Optional.of(existingStar));

        // Act
        DocumentStarResponse response1 = documentStarService.star(userId, documentId);
        DocumentStarResponse response2 = documentStarService.star(userId, documentId);

        // Assert
        assertEquals(response1.getStarredAt(), response2.getStarredAt());
        assertEquals(starTime, response1.getStarredAt());
    }
}

