package com.docusphere.backend.admin.team.service;

import com.docusphere.backend.admin.team.dto.AdminCreateTeamRequest;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentShare.repository.DocumentShareRepository;
import com.docusphere.backend.documentStar.repository.DocumentStarRepository;
import com.docusphere.backend.team.dto.TeamDto;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.entity.TeamInvitation;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.repository.TeamInvitationRepository;
import com.docusphere.backend.team.repository.TeamMemberRepository;
import com.docusphere.backend.team.repository.TeamRepository;
import com.docusphere.backend.team.repository.UserActivityRepository;
import com.docusphere.backend.team.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminTeamCoreService Unit Tests")
class AdminTeamCoreServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private TeamInvitationRepository teamInvitationRepository;
    @Mock private UserRepository userRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentStarRepository documentStarRepository;
    @Mock private DocumentShareRepository documentShareRepository;
    @Mock private UserActivityRepository userActivityRepository;
    @Mock private TeamService teamService;

    @InjectMocks
    private AdminTeamCoreService adminTeamCoreService;

    private User leaderUser;
    private User regularUser;
    private Team mockTeam;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();

        leaderUser = new User();
        leaderUser.setId(1L);
        leaderUser.setEmail("leader@docusphere.com");
        leaderUser.setFullName("Captain Leader");

        regularUser = new User();
        regularUser.setId(2L);
        regularUser.setEmail("member@docusphere.com");
        regularUser.setFullName("Regular Member");

        mockTeam = new Team();
        mockTeam.setId(teamId);
        mockTeam.setTeamName("Test Team");
        mockTeam.setMemberCount(0);
    }

    @Test
    @DisplayName("Should successfully create a team with an explicit leader in additional members")
    void testCreateTeam_ExplicitLeader() {
        // Arrange
        AdminCreateTeamRequest request = new AdminCreateTeamRequest();
        request.setName("Test Team");
        request.setDescription("Test Description");

        AdminCreateTeamRequest.AdditionalMember leaderMember = new AdminCreateTeamRequest.AdditionalMember();
        leaderMember.setUserId(1L);
        leaderMember.setRole("LEADER");

        AdminCreateTeamRequest.AdditionalMember normalMember = new AdminCreateTeamRequest.AdditionalMember();
        normalMember.setUserId(2L);
        normalMember.setRole("MEMBER");

        request.setAdditionalMembers(List.of(leaderMember, normalMember));

        when(userRepository.findById(1L)).thenReturn(Optional.of(leaderUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(regularUser));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team t = invocation.getArgument(0);
            t.setId(teamId);
            return t;
        });

        TeamDto mockDto = new TeamDto();
        mockDto.setId(teamId);
        mockDto.setName("Test Team");
        when(teamService.toDto(any(Team.class))).thenReturn(mockDto);

        // Act
        TeamDto result = adminTeamCoreService.createTeam(request);

        // Assert
        assertNotNull(result);
        assertEquals(teamId, result.getId());
        verify(teamMemberRepository, times(2)).save(any(TeamMember.class));
    }

    @Test
    @DisplayName("Should successfully create a team with fallback leaderId")
    void testCreateTeam_FallbackLeaderId() {
        // Arrange
        AdminCreateTeamRequest request = new AdminCreateTeamRequest();
        request.setName("Fallback Team");
        request.setLeaderId(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(leaderUser));
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team t = invocation.getArgument(0);
            t.setId(teamId);
            return t;
        });

        TeamDto mockDto = new TeamDto();
        mockDto.setId(teamId);
        when(teamService.toDto(any(Team.class))).thenReturn(mockDto);

        // Act
        TeamDto result = adminTeamCoreService.createTeam(request);

        // Assert
        assertNotNull(result);
        verify(teamMemberRepository, times(1)).save(any(TeamMember.class));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException if leader is missing")
    void testCreateTeam_NoLeader() {
        // Arrange
        AdminCreateTeamRequest request = new AdminCreateTeamRequest();
        request.setName("No Leader Team");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> adminTeamCoreService.createTeam(request));
    }

    @Test
    @DisplayName("Should delete team and clean up all resources")
    void testDeleteTeam_Success() {
        // Arrange
        Document doc1 = Document.builder().id(UUID.randomUUID()).build();
        when(documentRepository.findAllByTeamId(teamId)).thenReturn(List.of(doc1));

        // Act
        adminTeamCoreService.deleteTeam(teamId);

        // Assert
        verify(documentStarRepository, times(1)).deleteByDocumentIdIn(anyList());
        verify(documentShareRepository, times(1)).deleteByDocumentIdIn(anyList());
        verify(documentRepository, times(1)).deleteByTeamId(teamId);
        verify(userActivityRepository, times(1)).deleteByTeamId(teamId);
        verify(teamMemberRepository, times(1)).deleteByTeamId(teamId);
        verify(teamInvitationRepository, times(1)).deleteByTeamId(teamId);
        verify(teamRepository, times(1)).deleteById(teamId);
    }
}
