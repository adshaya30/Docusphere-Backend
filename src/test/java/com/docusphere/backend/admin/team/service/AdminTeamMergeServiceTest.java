package com.docusphere.backend.admin.team.service;

import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.entity.TeamInvitation;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.repository.TeamInvitationRepository;
import com.docusphere.backend.team.repository.TeamMemberRepository;
import com.docusphere.backend.team.repository.TeamRepository;
import com.docusphere.backend.document.repository.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
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
@DisplayName("AdminTeamMergeService Unit Tests")
class AdminTeamMergeServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private TeamInvitationRepository teamInvitationRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private AdminTeamQueryService queryService;

    @InjectMocks
    private AdminTeamMergeService adminTeamMergeService;

    private UUID sourceId;
    private UUID targetId;
    private Team sourceTeam;
    private Team targetTeam;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID();
        targetId = UUID.randomUUID();

        sourceTeam = new Team();
        sourceTeam.setId(sourceId);
        sourceTeam.setTeamName("Source Team");

        targetTeam = new Team();
        targetTeam.setId(targetId);
        targetTeam.setTeamName("Target Team");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when merging same team")
    void testMergeTeams_SameTeam() {
        assertThrows(IllegalArgumentException.class, () ->
            adminTeamMergeService.mergeTeams(sourceId, sourceId, "Merged Team", 1L, true)
        );
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when target team name already exists")
    void testMergeTeams_NameExists() {
        when(teamRepository.existsByTeamName("Merged Team")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () ->
            adminTeamMergeService.mergeTeams(sourceId, targetId, "Merged Team", 1L, true)
        );
    }

    @Test
    @DisplayName("Should successfully merge teams and copy members and documents")
    void testMergeTeams_Success() {
        // Arrange
        String newName = "Merged Super Team";
        when(teamRepository.existsByTeamName(newName)).thenReturn(false);
        when(queryService.assertTeamExists(sourceId)).thenReturn(sourceTeam);
        when(queryService.assertTeamExists(targetId)).thenReturn(targetTeam);

        UUID mergedId = UUID.randomUUID();
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team t = invocation.getArgument(0);
            if (t.getId() == null) {
                t.setId(mergedId);
            }
            return t;
        });

        // Setup members to copy
        TeamMember m1 = new TeamMember();
        m1.setUserId(1L);
        m1.setFullName("User One");
        m1.setRole(TeamRole.LEADER);

        TeamMember m2 = new TeamMember();
        m2.setUserId(2L);
        m2.setFullName("User Two");
        m2.setRole(TeamRole.MEMBER);

        when(teamMemberRepository.findAllByTeamId(sourceId)).thenReturn(List.of(m1));
        when(teamMemberRepository.findAllByTeamId(targetId)).thenReturn(List.of(m2));

        // Mock leadership promotion
        TeamMember leaderInMerged = new TeamMember();
        leaderInMerged.setUserId(1L);
        leaderInMerged.setRole(TeamRole.MEMBER);
        when(teamMemberRepository.findByUserIdAndTeamId(1L, mergedId)).thenReturn(Optional.of(leaderInMerged));

        // Act
        Team result = adminTeamMergeService.mergeTeams(sourceId, targetId, newName, 1L, true);

        // Assert
        assertNotNull(result);
        assertEquals(newName, result.getTeamName());
        assertEquals(2, result.getMemberCount());
        assertEquals(TeamRole.LEADER, leaderInMerged.getRole()); // User 1 promoted to Leader

        verify(teamMemberRepository, times(3)).save(any(TeamMember.class));
        verify(documentRepository, times(1)).updateAllTeamId(sourceId, mergedId);
        verify(documentRepository, times(1)).updateAllTeamId(targetId, mergedId);
    }
}
