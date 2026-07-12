package com.docusphere.backend.admin.team.service;

import com.docusphere.backend.Common.config.AppConfig;
import com.docusphere.backend.admin.team.dto.AdminMemberView;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.EmailService;
import com.docusphere.backend.team.dto.AddMemberRequest;
import com.docusphere.backend.team.dto.TransferLeaderRequest;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.entity.TeamInvitation;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.repository.TeamInvitationRepository;
import com.docusphere.backend.team.repository.TeamMemberRepository;
import com.docusphere.backend.team.repository.TeamRepository;
import com.docusphere.backend.notification.service.NotificationHelper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminTeamMemberService Unit Tests")
class AdminTeamMemberServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private TeamInvitationRepository teamInvitationRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private AppConfig appConfig;
    @Mock private AdminTeamQueryService queryService;
    @Mock private NotificationHelper notificationHelper;

    @InjectMocks
    private AdminTeamMemberService adminTeamMemberService;

    private UUID teamId;
    private Team mockTeam;
    private User adminUser;
    private User targetUser;
    private SecurityContext originalContext;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        mockTeam = new Team();
        mockTeam.setId(teamId);
        mockTeam.setTeamName("Delta Team");

        adminUser = new User();
        adminUser.setId(99L);
        adminUser.setEmail("admin@docusphere.com");
        adminUser.setFullName("Super Admin");

        targetUser = new User();
        targetUser.setId(22L);
        targetUser.setEmail("target@docusphere.com");
        targetUser.setFullName("Target User");

        originalContext = SecurityContextHolder.getContext();
        SecurityContext context = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn("admin@docusphere.com");
        lenient().when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.setContext(originalContext);
    }

    @Test
    @DisplayName("Should successfully add a member by userId when not already a member")
    void testAddMember_ByUserId_Success() {
        // Arrange
        AddMemberRequest request = new AddMemberRequest();
        request.setUserId(22L);
        request.setRole("MEMBER");

        when(queryService.assertTeamExists(teamId)).thenReturn(mockTeam);
        when(userRepository.findByEmail("admin@docusphere.com")).thenReturn(Optional.of(adminUser));
        when(teamMemberRepository.existsByUserIdAndTeamId(22L, teamId)).thenReturn(false);
        when(userRepository.findById(22L)).thenReturn(Optional.of(targetUser));

        TeamInvitation savedInvite = new TeamInvitation();
        savedInvite.setId(UUID.randomUUID());
        savedInvite.setEmail("target@docusphere.com");
        savedInvite.setTeamId(teamId);
        savedInvite.setRole(TeamRole.MEMBER);

        when(teamInvitationRepository.save(any(TeamInvitation.class))).thenReturn(savedInvite);
        when(queryService.toAdminMemberViewFromInvitation(savedInvite)).thenReturn(new AdminMemberView());

        // Act
        AdminMemberView result = adminTeamMemberService.addMember(teamId, request);

        // Assert
        assertNotNull(result);
        verify(teamInvitationRepository, times(1)).save(any(TeamInvitation.class));
    }

    @Test
    @DisplayName("Should throw IllegalStateException if member is already in the team")
    void testAddMember_AlreadyMember() {
        // Arrange
        AddMemberRequest request = new AddMemberRequest();
        request.setUserId(22L);
        request.setRole("MEMBER");

        when(queryService.assertTeamExists(teamId)).thenReturn(mockTeam);
        when(userRepository.findByEmail("admin@docusphere.com")).thenReturn(Optional.of(adminUser));
        when(teamMemberRepository.existsByUserIdAndTeamId(22L, teamId)).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> adminTeamMemberService.addMember(teamId, request));
    }

    @Test
    @DisplayName("Should delete member successfully and decrement count")
    void testDeleteMember_Success() {
        // Arrange
        TeamMember member = new TeamMember();
        member.setId(UUID.randomUUID());
        member.setUserId(22L);
        member.setRole(TeamRole.MEMBER);
        member.setTeam(mockTeam);

        when(teamMemberRepository.findByUserIdAndTeamId(22L, teamId)).thenReturn(Optional.of(member));

        // Act
        adminTeamMemberService.deleteMember(teamId, 22L);

        // Assert
        verify(teamMemberRepository, times(1)).delete(member);
        verify(teamRepository, times(1)).decrementMemberCount(teamId);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when attempting to delete the leader")
    void testDeleteMember_Leader_ThrowsException() {
        // Arrange
        TeamMember leader = new TeamMember();
        leader.setUserId(22L);
        leader.setRole(TeamRole.LEADER);
        leader.setTeam(mockTeam);

        when(teamMemberRepository.findByUserIdAndTeamId(22L, teamId)).thenReturn(Optional.of(leader));

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> adminTeamMemberService.deleteMember(teamId, 22L));
        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should update member role successfully")
    void testUpdateMemberRole_Success() {
        // Arrange
        TeamMember member = new TeamMember();
        member.setUserId(22L);
        member.setRole(TeamRole.MEMBER);
        member.setTeam(mockTeam);

        when(teamMemberRepository.findByUserIdAndTeamId(22L, teamId)).thenReturn(Optional.of(member));

        // Act
        adminTeamMemberService.updateMemberRole(teamId, 22L, "MANAGER");

        // Assert
        assertEquals(TeamRole.MANAGER, member.getRole());
        verify(teamMemberRepository, times(1)).save(member);
    }

    @Test
    @DisplayName("Should transfer leadership successfully")
    void testTransferLeader_Success() {
        // Arrange
        TeamMember oldLeader = new TeamMember();
        oldLeader.setUserId(1L);
        oldLeader.setRole(TeamRole.LEADER);
        oldLeader.setTeam(mockTeam);

        TeamMember newLeader = new TeamMember();
        newLeader.setUserId(2L);
        newLeader.setRole(TeamRole.MEMBER);
        newLeader.setTeam(mockTeam);

        TransferLeaderRequest request = new TransferLeaderRequest();
        request.setNewLeaderId(2L);

        when(teamMemberRepository.findLeaderByTeamId(teamId)).thenReturn(Optional.of(oldLeader));
        when(teamMemberRepository.findByUserIdAndTeamId(2L, teamId)).thenReturn(Optional.of(newLeader));

        // Act
        adminTeamMemberService.transferLeader(teamId, request);

        // Assert
        assertEquals(TeamRole.MEMBER, oldLeader.getRole());
        assertEquals(TeamRole.LEADER, newLeader.getRole());
        verify(teamMemberRepository, times(1)).save(oldLeader);
        verify(teamMemberRepository, times(1)).save(newLeader);
    }
}
