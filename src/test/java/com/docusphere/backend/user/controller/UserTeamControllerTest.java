package com.docusphere.backend.user.controller;

import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.team.service.TeamService;
import com.docusphere.backend.user.services.UserDocumentService;
import com.docusphere.backend.user.services.UserTeamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserTeamController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserTeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserTeamService userTeamService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private JwtService jwtService;

    @AfterEach
    void resetMocks() {
        reset(userTeamService, teamService, jwtService);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public UserTeamService userTeamService() {
            return mock(UserTeamService.class);
        }

        @Bean
        @Primary
        public TeamService teamService() {
            return mock(TeamService.class);
        }

        @Bean
        @Primary
        public UserDocumentService userDocumentService() {
            return mock(UserDocumentService.class);
        }

        @Bean
        @Primary
        public JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        @Primary
        public UserDetailsService userDetailsService() {
            return mock(UserDetailsService.class);
        }
    }

    @Test
    @DisplayName("PUT /api/teams/{teamId}/members/{memberId}/chat-block - Success")
    void updateMemberChatBlock_success() throws Exception {
        UUID teamId = UUID.randomUUID();
        Long memberId = 2L;
        String token = "valid-token";
        Long requesterId = 1L;

        when(jwtService.extractUserId(token)).thenReturn(requesterId);
        when(teamService.isUserInTeam(requesterId, teamId)).thenReturn(true);

        mockMvc.perform(put("/api/teams/" + teamId + "/members/" + memberId + "/chat-block")
                        .header("Authorization", "Bearer " + token)
                        .param("blocked", "true")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(userTeamService).setMemberChatAccess(eq(teamId), eq(memberId), eq(true), eq(requesterId));
    }
}
