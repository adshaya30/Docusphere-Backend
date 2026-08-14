package com.docusphere.backend.admin.report.service;

import com.docusphere.backend.authentication.entity.Role;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminReportExportService Unit Tests")
class AdminReportExportServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private AdminReportExportService reportExportService;

    private User mockUser;
    private Team mockTeam;
    private Document mockDoc;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(10L);
        mockUser.setFullName("Jane Smith");
        mockUser.setEmail("jane@docusphere.com");
        Role r = new Role();
        r.setName("ADMIN");
        mockUser.setRole(r);

        mockTeam = new Team();
        mockTeam.setId(UUID.randomUUID());
        mockTeam.setTeamName("Product Team");
        mockTeam.setMemberCount(4);
        mockTeam.setCreatedAt(LocalDateTime.now());

        mockDoc = Document.builder()
                .id(UUID.randomUUID())
                .name("Specs Document")
                .type("pdf")
                .sizeBytes(5000L)
                .ownerId(10L)
                .teamId(mockTeam.getId())
                .status(Document.UploadStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should successfully export users in PDF, CSV, and XLSX formats")
    void testExportUsers_Success() throws Exception {
        // Arrange
        when(userRepository.findAll()).thenReturn(List.of(mockUser));

        // Act
        byte[] pdfBytes = reportExportService.exportUsersToPDF();
        byte[] csvBytes = reportExportService.exportUsersToCSV();
        byte[] xlsxBytes = reportExportService.exportUsersToXLSX();

        // Assert
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        assertNotNull(csvBytes);
        String csvContent = new String(csvBytes);
        assertTrue(csvContent.contains("User ID"));
        assertTrue(csvContent.contains("jane@docusphere.com"));

        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 0);
    }

    @Test
    @DisplayName("Should successfully export teams in PDF, CSV, and XLSX formats")
    void testExportTeams_Success() throws Exception {
        // Arrange
        when(teamRepository.findAll()).thenReturn(List.of(mockTeam));

        // Act
        byte[] pdfBytes = reportExportService.exportTeamsToPDF();
        byte[] csvBytes = reportExportService.exportTeamsToCSV();
        byte[] xlsxBytes = reportExportService.exportTeamsToXLSX();

        // Assert
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        assertNotNull(csvBytes);
        String csvContent = new String(csvBytes);
        assertTrue(csvContent.contains("Team Name"));
        assertTrue(csvContent.contains("Product Team"));

        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 0);
    }

    @Test
    @DisplayName("Should successfully export documents in PDF, CSV, and XLSX formats")
    void testExportDocuments_Success() throws Exception {
        // Arrange
        when(documentRepository.findAll()).thenReturn(List.of(mockDoc));
        when(userRepository.findById(10L)).thenReturn(Optional.of(mockUser));
        when(teamRepository.findById(mockDoc.getTeamId())).thenReturn(Optional.of(mockTeam));

        // Act
        byte[] pdfBytes = reportExportService.exportDocumentsToPDF();
        byte[] csvBytes = reportExportService.exportDocumentsToCSV();
        byte[] xlsxBytes = reportExportService.exportDocumentsToXLSX();

        // Assert
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);

        assertNotNull(csvBytes);
        String csvContent = new String(csvBytes);
        assertTrue(csvContent.contains("Specs Document"));

        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 0);
    }
}
