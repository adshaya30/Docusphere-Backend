package com.docusphere.backend.user.services;

import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.EmailService;
import com.docusphere.backend.Common.config.AppConfig;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.documentStar.repository.DocumentStarRepository;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.entity.TeamInvitation;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.repository.TeamInvitationRepository;
import com.docusphere.backend.team.repository.TeamMemberRepository;
import com.docusphere.backend.team.repository.TeamRepository;
import com.docusphere.backend.team.repository.UserActivityRepository;
import com.docusphere.backend.team.service.TeamService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import com.docusphere.backend.team.dto.AddMemberRequest;
import com.docusphere.backend.team.dto.TeamDto;
import com.docusphere.backend.team.dto.TransferLeaderRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserTeamServiceTest {

    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private TeamInvitationRepository teamInvitationRepository;
    @Mock private TeamService teamService;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private AppConfig appConfig;
    @Mock private DocumentRepository documentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private UserActivityRepository userActivityRepository;
    @Mock private DocumentStarRepository documentStarRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private UserTeamService userTeamService;

    private UUID teamId;
    private Long memberUserId;
    private Long requesterId;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        memberUserId = 2L;
        requesterId = 1L;
    }

    @Test
    @DisplayName("createTeam fails when name is blank")
    void createTeam_whenNameBlank_shouldThrowIllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> userTeamService.createTeam("   ", "desc", null, requesterId));

        assertEquals("Team name is required", exception.getMessage());
        verify(teamRepository, never()).save(any());
    }

    @Test
    @DisplayName("createTeam creates leader membership and returns DTO")
    void createTeam_success_shouldCreateTeamAndLeaderMembership() {
        User leader = new User();
        leader.setId(requesterId);
        leader.setFullName("Alice");

        Team savedTeam = new Team();
        savedTeam.setId(teamId);
        savedTeam.setTeamName("Design Team");
        savedTeam.setDescription("Product design");

        when(teamRepository.existsByTeamName("Design Team")).thenReturn(false);
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(leader));
        when(teamRepository.save(any(Team.class))).thenReturn(savedTeam);
        when(teamService.toDto(savedTeam)).thenReturn(new TeamDto(teamId, "Design Team", "Product design", 1, 0, null, null));

        TeamDto result = userTeamService.createTeam("Design Team", "Product design", null, requesterId);

        assertNotNull(result);
        assertEquals("Design Team", result.getName());
        verify(teamMemberRepository).save(argThat(member ->
                member.getUserId().equals(requesterId)
                        && member.getRole() == TeamRole.LEADER
                        && member.getTeam().getId().equals(teamId)
        ));
    }

    @Test
    @DisplayName("removeMember fails when requester is not LEADER")
    void removeMember_whenRequesterNotLeader_shouldThrowIllegalStateException() {
        when(teamService.isUserRoleInTeam(requesterId, teamId, TeamRole.LEADER)).thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> userTeamService.removeMember(teamId, memberUserId, requesterId));

        assertEquals("Only the LEADER can remove members", exception.getMessage());
    }

    @Test
    @DisplayName("removeMember succeeds and removes team membership")
    void removeMember_success_shouldDeleteMembership() {
        TeamMember membership = new TeamMember();
        membership.setUserId(memberUserId);
        membership.setFullName("Bob");
        membership.setRole(TeamRole.MEMBER);

        Team team = new Team();
        team.setId(teamId);
        team.setTeamName("Ops Team");

        when(teamService.isUserRoleInTeam(requesterId, teamId, TeamRole.LEADER)).thenReturn(true);
        when(teamMemberRepository.findByUserIdAndTeamId(memberUserId, teamId)).thenReturn(Optional.of(membership));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        userTeamService.removeMember(teamId, memberUserId, requesterId);

        verify(teamMemberRepository).delete(membership);
        verify(teamRepository).decrementMemberCount(teamId);
    }

    @Test
    @DisplayName("setMemberChatAccess fails when requester is not a LEADER")
    void setMemberChatAccess_whenRequesterNotLeader_shouldThrowIllegalStateException() {
        when(teamService.isUserRoleInTeam(requesterId, teamId, TeamRole.LEADER)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
            userTeamService.setMemberChatAccess(teamId, memberUserId, true, requesterId)
        );

        verify(teamMemberRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("setMemberChatAccess fails when member is not found in team")
    void setMemberChatAccess_whenMemberNotFound_shouldThrowEntityNotFoundException() {
        when(teamService.isUserRoleInTeam(requesterId, teamId, TeamRole.LEADER)).thenReturn(true);
        when(teamMemberRepository.findByUserIdAndTeamId(memberUserId, teamId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () ->
            userTeamService.setMemberChatAccess(teamId, memberUserId, true, requesterId)
        );
    }

    @Test
    @DisplayName("setMemberChatAccess fails when trying to block the LEADER")
    void setMemberChatAccess_whenBlockingLeader_shouldThrowIllegalStateException() {
        when(teamService.isUserRoleInTeam(requesterId, teamId, TeamRole.LEADER)).thenReturn(true);
        TeamMember membership = new TeamMember();
        membership.setRole(TeamRole.LEADER);
        when(teamMemberRepository.findByUserIdAndTeamId(memberUserId, teamId)).thenReturn(Optional.of(membership));

        assertThrows(IllegalStateException.class, () ->
            userTeamService.setMemberChatAccess(teamId, memberUserId, true, requesterId)
        );
    }

    @Test
    @DisplayName("setMemberChatAccess succeeds and broadcasts event")
    void setMemberChatAccess_success_shouldSaveAndBroadcast() {
        when(teamService.isUserRoleInTeam(requesterId, teamId, TeamRole.LEADER)).thenReturn(true);
        TeamMember membership = new TeamMember();
        membership.setRole(TeamRole.MEMBER);
        membership.setActive(true);
        when(teamMemberRepository.findByUserIdAndTeamId(memberUserId, teamId)).thenReturn(Optional.of(membership));

        userTeamService.setMemberChatAccess(teamId, memberUserId, true, requesterId);

        verify(teamMemberRepository).save(membership);
        verify(messagingTemplate).convertAndSend(
            eq("/topic/teams/" + teamId + "/chat"),
            any(Object.class)
        );
    }

    @Test
    @DisplayName("acceptInvitation adds a member when the invited email matches the user")
    void acceptInvitation_success_shouldAddMemberToTeam() {
        UUID invitationId = UUID.randomUUID();
        TeamInvitation invitation = new TeamInvitation();
        invitation.setId(invitationId);
        invitation.setEmail("alice@example.com");
        invitation.setTeamId(teamId);
        invitation.setRole(TeamRole.MEMBER);
        invitation.setInviterId(requesterId);

        User user = new User();
        user.setId(memberUserId);
        user.setEmail("alice@example.com");
        user.setFullName("Alice");

        Team team = new Team();
        team.setId(teamId);
        team.setTeamName("Ops Team");

        when(teamInvitationRepository.findById(invitationId)).thenReturn(Optional.of(invitation));
        when(userRepository.findById(memberUserId)).thenReturn(Optional.of(user));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByUserIdAndTeamId(memberUserId, teamId)).thenReturn(false);

        userTeamService.acceptInvitation(invitationId, memberUserId);

        verify(teamMemberRepository).save(any(TeamMember.class));
        verify(teamRepository).incrementMemberCount(teamId);
        verify(teamInvitationRepository).delete(invitation);
    }
}
