package com.docusphere.backend.admin.search.service;

import com.docusphere.backend.admin.search.dto.GlobalSearchResultDTO;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminGlobalSearchService Unit Tests")
class AdminGlobalSearchServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private AdminGlobalSearchService searchService;

    private User mockUser;
    private Team mockTeam;
    private Document mockDoc;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setFullName("Arthur Dent");
        mockUser.setEmail("arthur@galaxy.com");
        Role r = new Role();
        r.setName("USER");
        mockUser.setRole(r);

        mockTeam = new Team();
        mockTeam.setId(UUID.randomUUID());
        mockTeam.setTeamName("Hitchhikers");
        mockTeam.setMemberCount(42);

        mockDoc = Document.builder()
                .id(UUID.randomUUID())
                .name("Guide book.pdf")
                .type("pdf")
                .build();
    }

    @Test
    @DisplayName("Should return empty result for blank or null query")
    void testGlobalSearch_EmptyOrNullQuery() {
        // Act
        GlobalSearchResultDTO resultNull = searchService.globalSearch(null);
        GlobalSearchResultDTO resultEmpty = searchService.globalSearch("   ");

        // Assert
        assertNull(resultNull.getUsers());
        assertNull(resultNull.getTeams());
        assertNull(resultNull.getDocuments());
        assertNull(resultNull.getTotalResults());

        assertNull(resultEmpty.getUsers());
        assertNull(resultEmpty.getTeams());
        assertNull(resultEmpty.getDocuments());
        assertNull(resultEmpty.getTotalResults());
    }

    @Test
    @DisplayName("Should match search terms across users, teams, and documents")
    void testGlobalSearch_Matches() {
        // Arrange
        when(userRepository.findAll()).thenReturn(List.of(mockUser));
        when(teamRepository.findAll()).thenReturn(List.of(mockTeam));
        when(documentRepository.findAll()).thenReturn(List.of(mockDoc));

        // Act
        GlobalSearchResultDTO result = searchService.globalSearch("Dent");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getUsers().size());
        assertEquals("Arthur Dent", result.getUsers().get(0).getFullName());
        assertEquals("user", result.getUsers().get(0).getType());

        // Hitchhikers does not contain "Dent", Guide book does not contain "Dent"
        assertEquals(0, result.getTeams().size());
        assertEquals(0, result.getDocuments().size());
        assertEquals(1L, result.getTotalResults());
    }

    @Test
    @DisplayName("Should match multiple items correctly")
    void testGlobalSearch_MultipleMatches() {
        // Arrange
        mockUser.setFullName("CommonName User");
        mockTeam.setTeamName("CommonName Team");
        mockDoc.setName("CommonName Document");

        when(userRepository.findAll()).thenReturn(List.of(mockUser));
        when(teamRepository.findAll()).thenReturn(List.of(mockTeam));
        when(documentRepository.findAll()).thenReturn(List.of(mockDoc));

        // Act
        GlobalSearchResultDTO result = searchService.globalSearch("commonname");

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getUsers().size());
        assertEquals(1, result.getTeams().size());
        assertEquals(1, result.getDocuments().size());
        assertEquals(3L, result.getTotalResults());
    }
}
