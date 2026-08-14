package com.docusphere.backend.admin.document.services;

import com.docusphere.backend.admin.document.dto.AdminDocumentView;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.service.DocumentService;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.repository.TeamRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDocumentService Unit Tests")
class AdminDocumentServiceTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private AdminDocumentService adminDocumentService;

    private Document mockDoc;
    private User mockUser;
    private Team mockTeam;
    private UUID docId;
    private UUID teamId;
    private Long ownerId;

    @BeforeEach
    void setUp() {
        docId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        ownerId = 100L;

        mockDoc = Document.builder()
                .id(docId)
                .fileId("file-99")
                .name("Important Report")
                .type("docx")
                .sizeBytes(2048L)
                .ownerId(ownerId)
                .teamId(teamId)
                .status(Document.UploadStatus.COMPLETED)
                .secured(true)
                .storageKey("key/report")
                .fileUrl("http://docusphere.com/report")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        mockUser = new User();
        mockUser.setId(ownerId);
        mockUser.setEmail("owner@docusphere.com");
        mockUser.setFullName("John Owner");

        mockTeam = new Team();
        mockTeam.setId(teamId);
        mockTeam.setTeamName("Core Dev Team");
    }

    @Test
    @DisplayName("Should retrieve all documents successfully with enriched information")
    void testGetAllDocuments_Success() {
        // Arrange
        when(documentRepository.findAll()).thenReturn(List.of(mockDoc));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(mockUser));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(mockTeam));

        // Act
        List<AdminDocumentView> results = adminDocumentService.getAllDocuments();

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        AdminDocumentView view = results.get(0);
        assertEquals(docId, view.getId());
        assertEquals("owner@docusphere.com", view.getOwnerEmail());
        assertEquals("John Owner", view.getOwnerFullName());
        assertEquals("Core Dev Team", view.getTeamName());
    }

    @Test
    @DisplayName("Should retrieve documents by team ID successfully")
    void testGetDocumentsByTeam_Success() {
        // Arrange
        when(documentRepository.findAllByTeamId(teamId)).thenReturn(List.of(mockDoc));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(mockUser));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(mockTeam));

        // Act
        List<AdminDocumentView> results = adminDocumentService.getDocumentsByTeam(teamId);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(docId, results.get(0).getId());
    }

    @Test
    @DisplayName("Should retrieve a single document by ID successfully")
    void testGetDocument_Success() {
        // Arrange
        when(documentRepository.findById(docId)).thenReturn(Optional.of(mockDoc));
        when(userRepository.findById(ownerId)).thenReturn(Optional.of(mockUser));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(mockTeam));

        // Act
        AdminDocumentView result = adminDocumentService.getDocument(docId);

        // Assert
        assertNotNull(result);
        assertEquals(docId, result.getId());
        assertEquals("John Owner", result.getOwnerFullName());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when document is not found")
    void testGetDocument_NotFound() {
        // Arrange
        when(documentRepository.findById(docId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> adminDocumentService.getDocument(docId));
    }

    @Test
    @DisplayName("Should permanently delete a document successfully")
    void testDeleteDocument_Success() {
        // Arrange
        when(documentRepository.findById(docId)).thenReturn(Optional.of(mockDoc));

        // Act
        adminDocumentService.deleteDocument(docId);

        // Assert
        verify(documentService, times(1)).deleteById(docId);
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException when attempting to delete non-existent document")
    void testDeleteDocument_NotFound() {
        // Arrange
        when(documentRepository.findById(docId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> adminDocumentService.deleteDocument(docId));
        verify(documentService, never()).deleteById(any());
    }
}
