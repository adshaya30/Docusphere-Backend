package com.docusphere.backend.admin.team.service;

import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentShare.repository.DocumentShareRepository;
import com.docusphere.backend.documentStar.repository.DocumentStarRepository;
import com.docusphere.backend.team.document.dto.DocumentSummaryDto;
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
@DisplayName("AdminTeamDocumentService Unit Tests")
class AdminTeamDocumentServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentStarRepository documentStarRepository;
    @Mock private DocumentShareRepository documentShareRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private AdminTeamDocumentService adminTeamDocumentService;

    private UUID teamId;
    private UUID docId;
    private Team mockTeam;
    private Document mockDoc;
    private User mockUser;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        docId = UUID.randomUUID();

        mockTeam = new Team();
        mockTeam.setId(teamId);
        mockTeam.setTeamName("Alpha Team");

        mockDoc = Document.builder()
                .id(docId)
                .name("Budget report.xlsx")
                .type("xlsx")
                .sizeBytes(4096L)
                .ownerId(10L)
                .teamId(teamId)
                .createdAt(LocalDateTime.now())
                .build();

        mockUser = new User();
        mockUser.setId(10L);
        mockUser.setFullName("User Ten");
    }

    @Test
    @DisplayName("Should successfully retrieve documents of a team")
    void testGetTeamDocuments_Success() {
        // Arrange
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(mockTeam));
        when(documentRepository.findAllByTeamId(teamId)).thenReturn(List.of(mockDoc));
        when(userRepository.findById(10L)).thenReturn(Optional.of(mockUser));

        // Act
        List<DocumentSummaryDto> results = adminTeamDocumentService.getTeamDocuments(teamId);

        // Assert
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Budget report.xlsx", results.get(0).getName());
        assertEquals("User Ten", results.get(0).getUploadedBy());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException if team does not exist when fetching documents")
    void testGetTeamDocuments_TeamNotFound() {
        // Arrange
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> adminTeamDocumentService.getTeamDocuments(teamId));
    }

    @Test
    @DisplayName("Should permanently delete a team document, related stars, shares, and decrement count")
    void testDeleteDocumentPermanently_Success() {
        // Arrange
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(mockTeam));
        when(documentRepository.findById(docId)).thenReturn(Optional.of(mockDoc));

        // Act
        adminTeamDocumentService.deleteDocumentPermanently(teamId, docId);

        // Assert
        verify(documentStarRepository, times(1)).deleteByDocumentIdIn(List.of(docId));
        verify(documentShareRepository, times(1)).deleteByDocumentIdIn(List.of(docId));
        verify(documentRepository, times(1)).delete(mockDoc);
        verify(teamRepository, times(1)).decrementDocumentCount(teamId);
    }
}
