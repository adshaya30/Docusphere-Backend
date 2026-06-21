package com.docusphere.backend.chat.service;

import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.chat.dto.TeamChatMessageRequest;
import com.docusphere.backend.chat.dto.TeamChatMessageResponse;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamChatServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TeamService teamService;

    @InjectMocks
    private TeamChatService teamChatService;

    private UUID teamId;
    private Long userId;

    @BeforeEach
    void setUp() {
        teamId = UUID.randomUUID();
        userId = 1L;
    }

    @Test
    @DisplayName("getMessages fails when user is not active in the team")
    void getMessages_whenUserNotMember_shouldThrowUnauthorizedAccessException() {
        when(teamService.findMembership(userId, teamId)).thenReturn(Optional.empty());

        assertThrows(UnauthorizedAccessException.class, () ->
                teamChatService.getMessages(teamId, userId, 50)
        );

        verify(jdbcTemplate, never()).query(anyString(), any(RowMapper.class), any(), any(), any());
    }

    @Test
    @DisplayName("getMessages succeeds when user is active in the team")
    void getMessages_success() {
        TeamMember membership = new TeamMember();
        membership.setActive(true);
        when(teamService.findMembership(userId, teamId)).thenReturn(Optional.of(membership));

        teamChatService.getMessages(teamId, userId, 50);

        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(teamId), eq(userId), eq(50));
    }

    @Test
    @DisplayName("createMessage fails when message content is blank")
    void createMessage_whenContentBlank_shouldThrowIllegalArgumentException() {
        TeamMember membership = new TeamMember();
        membership.setActive(true);
        when(teamService.findMembership(userId, teamId)).thenReturn(Optional.of(membership));

        TeamChatMessageRequest request = new TeamChatMessageRequest();
        request.setContent("   ");

        assertThrows(IllegalArgumentException.class, () ->
                teamChatService.createMessage(teamId, userId, request)
        );
    }
}
