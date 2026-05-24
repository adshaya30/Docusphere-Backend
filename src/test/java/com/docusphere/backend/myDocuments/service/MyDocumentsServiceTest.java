package com.docusphere.backend.myDocuments.service;

import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentStar.entity.DocumentStar;
import com.docusphere.backend.documentStar.repository.DocumentStarRepository;
import com.docusphere.backend.myDocuments.dto.MyDocumentItemResponse;
import com.docusphere.backend.myDocuments.dto.MyDocumentsPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MyDocumentsService Unit Tests")
@SuppressWarnings("unchecked")
class MyDocumentsServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentStarRepository starRepository;

    @Captor
    private ArgumentCaptor<Specification<Document>> specCaptor;

    private MyDocumentsService myDocumentsService;

    private Long ownerId;
    private UUID teamId;
    private Document mockDocument;

    @BeforeEach
    void setUp() {
        myDocumentsService = new MyDocumentsService(documentRepository, starRepository);
        ownerId = 123L;
        teamId = UUID.randomUUID();
        
        mockDocument = Document.builder()
                .id(UUID.randomUUID())
                .fileId("file123")
                .name("Test Document")
                .type("pdf")
                .sizeBytes(1024L)
                .ownerId(ownerId)
                .teamId(teamId)
                .storageKey("storage/key")
                .fileUrl("http://example.com/file")
                .status(Document.UploadStatus.COMPLETED)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ==================== GET DOCUMENTS TESTS ====================

    @Test
    @DisplayName("Should successfully fetch user documents without team")
    void testGetDocuments_UserSpace_Success() {
        // Arrange
        Page<Document> mockPage = new PageImpl<>(
                Collections.singletonList(mockDocument),
                org.springframework.data.domain.PageRequest.of(0, 15),
                1
        );

        when(starRepository.findByUserId(ownerId)).thenReturn(Collections.emptyList());
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        MyDocumentsPageResponse response = myDocumentsService.getDocuments(
                ownerId, null, 0, 15, "createdAt", "DESC", null, null, null
        );

        // Assert
        assertNotNull(response);
        assertEquals(0, response.getPage());
        assertEquals(15, response.getSize());
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getTotalPages());
        assertTrue(response.isFirst());
        assertTrue(response.isLast());
        assertFalse(response.isEmpty());
        assertEquals(1, response.getItems().size());
        
        verify(documentRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
        verify(starRepository, times(1)).findByUserId(ownerId);
    }

    @Test
    @DisplayName("Should fetch team documents with teamId")
    void testGetDocuments_WithTeam_Success() {
        // Arrange
        mockDocument.setTeamId(teamId);
        Page<Document> mockPage = new PageImpl<>(
                Collections.singletonList(mockDocument),
                org.springframework.data.domain.PageRequest.of(0, 15),
                1
        );

        when(starRepository.findByUserId(ownerId)).thenReturn(Collections.emptyList());
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        MyDocumentsPageResponse response = myDocumentsService.getDocuments(
                ownerId, teamId, 0, 15, "createdAt", "DESC", null, null, null
        );

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals(teamId.toString(), response.getItems().get(0).getTeamId());
        
        verify(documentRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Should filter documents by type")
    void testGetDocuments_FilterByType_Success() {
        // Arrange
        Page<Document> mockPage = new PageImpl<>(
                Collections.singletonList(mockDocument),
                org.springframework.data.domain.PageRequest.of(0, 15),
                1
        );

        when(starRepository.findByUserId(ownerId)).thenReturn(Collections.emptyList());
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        MyDocumentsPageResponse response = myDocumentsService.getDocuments(
                ownerId, null, 0, 15, "createdAt", "DESC", "pdf", null, null
        );

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals("pdf", response.getItems().get(0).getType());
    }

    @Test
    @DisplayName("Should filter documents by search term")
    void testGetDocuments_FilterBySearch_Success() {
        // Arrange
        Page<Document> mockPage = new PageImpl<>(
                Collections.singletonList(mockDocument),
                org.springframework.data.domain.PageRequest.of(0, 15),
                1
        );

        when(starRepository.findByUserId(ownerId)).thenReturn(Collections.emptyList());
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        MyDocumentsPageResponse response = myDocumentsService.getDocuments(
                ownerId, null, 0, 15, "createdAt", "DESC", null, null, "Test"
        );

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertTrue(response.getItems().get(0).getName().contains("Test"));
    }

    @Test
    @DisplayName("Should filter starred documents")
    void testGetDocuments_FilterStarred_Success() {
        // Arrange
        DocumentStar star = DocumentStar.builder()
                .userId(ownerId)
                .documentId(mockDocument.getId())
                .starredAt(LocalDateTime.now())
                .build();

        Page<Document> mockPage = new PageImpl<>(
                Collections.singletonList(mockDocument),
                org.springframework.data.domain.PageRequest.of(0, 15),
                1
        );

        when(starRepository.findByUserId(ownerId)).thenReturn(Collections.singletonList(star));
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        MyDocumentsPageResponse response = myDocumentsService.getDocuments(
                ownerId, null, 0, 15, "createdAt", "DESC", null, true, null
        );

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertTrue(response.getItems().get(0).isStarred());
    }

    @Test
    @DisplayName("Should filter non-starred documents")
    void testGetDocuments_FilterNonStarred_Success() {
        // Arrange
        Page<Document> mockPage = new PageImpl<>(
                Collections.singletonList(mockDocument),
                org.springframework.data.domain.PageRequest.of(0, 15),
                1
        );

        when(starRepository.findByUserId(ownerId)).thenReturn(Collections.emptyList());
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        MyDocumentsPageResponse response = myDocumentsService.getDocuments(
                ownerId, null, 0, 15, "createdAt", "DESC", null, false, null
        );

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertFalse(response.getItems().get(0).isStarred());
    }

    @Test
    @DisplayName("Should handle pagination correctly")
    void testGetDocuments_Pagination_Success() {
        // Arrange
        int pageNumber = 2;
        int pageSize = 10;
        
        Page<Document> mockPage = new PageImpl<>(
                Collections.nCopies(5, mockDocument),
                org.springframework.data.domain.PageRequest.of(pageNumber, pageSize),
                25
        );

        when(starRepository.findByUserId(ownerId)).thenReturn(Collections.emptyList());
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        MyDocumentsPageResponse response = myDocumentsService.getDocuments(
                ownerId, null, pageNumber, pageSize, "createdAt", "DESC", null, null, null
        );

        // Assert
        assertEquals(pageNumber, response.getPage());
        assertEquals(pageSize, response.getSize());
        assertEquals(mockPage.getNumberOfElements(), response.getItems().size());
        assertEquals(mockPage.getTotalPages(), response.getTotalPages());
        assertFalse(response.isFirst());
        assertTrue(response.isLast());
    }

    @Test
    @DisplayName("Should sort by different fields")
    void testGetDocuments_DifferentSort_Success() {
        // Arrange
        Page<Document> mockPage = new PageImpl<>(
                Collections.singletonList(mockDocument),
                org.springframework.data.domain.PageRequest.of(0, 15),
                1
        );

        when(starRepository.findByUserId(ownerId)).thenReturn(Collections.emptyList());
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        MyDocumentsPageResponse response = myDocumentsService.getDocuments(
                ownerId, null, 0, 15, "name", "ASC", null, null, null
        );

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        
        verify(documentRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("Should return empty page when no documents exist")
    void testGetDocuments_EmptyResult_Success() {
        // Arrange
        Page<Document> emptyPage = new PageImpl<>(
                Collections.emptyList(),
                org.springframework.data.domain.PageRequest.of(0, 15),
                0
        );

        when(starRepository.findByUserId(ownerId)).thenReturn(Collections.emptyList());
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        // Act
        MyDocumentsPageResponse response = myDocumentsService.getDocuments(
                ownerId, null, 0, 15, "createdAt", "DESC", null, null, null
        );

        // Assert
        assertTrue(response.isEmpty());
        assertEquals(0, response.getItems().size());
        assertEquals(0, response.getTotalElements());
    }

    @Test
    @DisplayName("Should map Document entity to DTO correctly")
    void testGetDocuments_EntityToDto_Success() {
        // Arrange
        UUID documentId = UUID.randomUUID();
        mockDocument.setId(documentId);
        mockDocument.setName("MyFile.pdf");
        mockDocument.setType("pdf");
        mockDocument.setSizeBytes(2048L);
        
        DocumentStar star = DocumentStar.builder()
                .userId(ownerId)
                .documentId(documentId)
                .starredAt(LocalDateTime.now())
                .build();

        Page<Document> mockPage = new PageImpl<>(
                Collections.singletonList(mockDocument),
                org.springframework.data.domain.PageRequest.of(0, 15),
                1
        );

        when(starRepository.findByUserId(ownerId)).thenReturn(Collections.singletonList(star));
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        MyDocumentsPageResponse response = myDocumentsService.getDocuments(
                ownerId, null, 0, 15, "createdAt", "DESC", null, null, null
        );

        // Assert
        MyDocumentItemResponse item = response.getItems().get(0);
        assertEquals(documentId.toString(), item.getId());
        assertEquals("MyFile.pdf", item.getName());
        assertEquals("pdf", item.getType());
        assertEquals(2048L, item.getSizeBytes());
        assertEquals(ownerId.toString(), item.getOwnerId());
        assertTrue(item.isStarred());
    }

    @Test
    @DisplayName("Should handle multiple documents correctly")
    void testGetDocuments_MultipleDocuments_Success() {
        // Arrange
        Document doc2 = Document.builder()
                .id(UUID.randomUUID())
                .fileId("file456")
                .name("Another Document")
                .type("docx")
                .sizeBytes(2048L)
                .ownerId(ownerId)
                .teamId(teamId)
                .storageKey("storage/key2")
                .fileUrl("http://example.com/file2")
                .status(Document.UploadStatus.COMPLETED)
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Page<Document> mockPage = new PageImpl<>(
                Arrays.asList(mockDocument, doc2),
                org.springframework.data.domain.PageRequest.of(0, 15),
                2
        );

        when(starRepository.findByUserId(ownerId)).thenReturn(Collections.emptyList());
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        MyDocumentsPageResponse response = myDocumentsService.getDocuments(
                ownerId, null, 0, 15, "createdAt", "DESC", null, null, null
        );

        // Assert
        assertEquals(2, response.getItems().size());
        assertEquals(2, response.getTotalElements());
        assertEquals("Test Document", response.getItems().get(0).getName());
        assertEquals("Another Document", response.getItems().get(1).getName());
    }

    @Test
    @DisplayName("Should handle null teamId for user space documents")
    void testGetDocuments_NullTeamIdHandling_Success() {
        // Arrange
        mockDocument.setTeamId(null);
        Page<Document> mockPage = new PageImpl<>(
                Collections.singletonList(mockDocument),
                org.springframework.data.domain.PageRequest.of(0, 15),
                1
        );

        when(starRepository.findByUserId(ownerId)).thenReturn(Collections.emptyList());
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        MyDocumentsPageResponse response = myDocumentsService.getDocuments(
                ownerId, null, 0, 15, "createdAt", "DESC", null, null, null
        );

        // Assert
        assertNotNull(response);
        assertNull(response.getItems().get(0).getTeamId());
    }

    @Test
    @DisplayName("Should handle documents with all filters applied")
    void testGetDocuments_AllFilters_Success() {
        // Arrange
        Page<Document> mockPage = new PageImpl<>(
                Collections.singletonList(mockDocument),
                org.springframework.data.domain.PageRequest.of(0, 10),
                1
        );

        DocumentStar star = DocumentStar.builder()
                .userId(ownerId)
                .documentId(mockDocument.getId())
                .starredAt(LocalDateTime.now())
                .build();

        when(starRepository.findByUserId(ownerId)).thenReturn(Collections.singletonList(star));
        when(documentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(mockPage);

        // Act
        MyDocumentsPageResponse response = myDocumentsService.getDocuments(
                ownerId, teamId, 0, 10, "name", "ASC", "pdf", true, "Test"
        );

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertTrue(response.getItems().get(0).isStarred());
    }
}


