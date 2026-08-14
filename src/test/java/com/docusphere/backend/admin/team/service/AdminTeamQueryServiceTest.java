package com.docusphere.backend.admin.team.service;

import com.docusphere.backend.admin.team.dto.AdminMemberView;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.entity.TeamInvitation;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.repository.TeamInvitationRepository;
import com.docusphere.backend.team.repository.TeamMemberRepository;
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
@DisplayName("AdminTeamQueryService Unit Tests")
class AdminTeamQueryServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private TeamInvitationRepository teamInvitationRepository;
    @Mock private UserRepository userRepository;
    @Mock private DocumentRepository documentRepository;

    @InjectMocks
    private AdminTeamQueryService adminTeamQueryService;

    private UUID teamId;
    private Team mockTeam;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        mockTeam = new Team();
        mockTeam.setId(teamId);
        mockTeam.setTeamName("Epsilon Team");
    }

    @Test
    @DisplayName("Should return Team if team exists, else throw EntityNotFoundException")
    void testAssertTeamExists() {
        // Arrange
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(mockTeam));

        // Act
        Team result = adminTeamQueryService.assertTeamExists(teamId);

        // Assert
        assertNotNull(result);
        assertEquals(teamId, result.getId());

        // Test exception
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> adminTeamQueryService.assertTeamExists(teamId));
    }

    @Test
    @DisplayName("Should return all team members combined with invitations")
    void testGetTeamMembers_Success() {
        // Arrange
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(mockTeam));

        TeamMember tm = new TeamMember();
        tm.setId(UUID.randomUUID());
        tm.setUserId(100L);
        tm.setFullName("Active Member");
        tm.setRole(TeamRole.MEMBER);
        tm.setTeam(mockTeam);
        tm.setLastSeen(LocalDateTime.now());

        TeamInvitation inv = new TeamInvitation();
        inv.setEmail("invitee@docusphere.com");
        inv.setRole(TeamRole.MEMBER);
        inv.setTeamId(teamId);

        when(teamMemberRepository.findAllByTeamId(teamId)).thenReturn(List.of(tm));
        when(teamInvitationRepository.findAllByTeamId(teamId)).thenReturn(List.of(inv));

        User user = new User();
        user.setId(100L);
        user.setEmail("active@docusphere.com");
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));

        // Act
        List<AdminMemberView> members = adminTeamQueryService.getTeamMembers(teamId);

        // Assert
        assertNotNull(members);
        assertEquals(2, members.size());

        // First member: Active Member
        AdminMemberView view1 = members.get(0);
        assertEquals("Active Member", view1.getFullName());
        assertEquals("Active", view1.getStatus()); // active within 7 days
        assertEquals("active@docusphere.com", view1.getEmail());

        // Second member: Invitee
        AdminMemberView view2 = members.get(1);
        assertEquals("Pending Invitation", view2.getFullName());
        assertEquals("Pending", view2.getStatus());
        assertEquals("invitee@docusphere.com", view2.getEmail());
    }
}
