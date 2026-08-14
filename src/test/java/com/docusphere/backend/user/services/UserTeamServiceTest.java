package com.docusphere.backend.user.services;

import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.EmailService;
import com.docusphere.backend.Common.config.AppConfig;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.documentStar.repository.DocumentStarRepository;
import com.docusphere.backend.team.entity.Team;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
