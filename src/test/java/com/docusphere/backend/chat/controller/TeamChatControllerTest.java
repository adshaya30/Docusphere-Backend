package com.docusphere.backend.chat.controller;

import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.chat.dto.TeamChatMessageRequest;
import com.docusphere.backend.chat.dto.TeamChatMessageResponse;
import com.docusphere.backend.chat.service.TeamChatService;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeamChatController.class)
@AutoConfigureMockMvc(addFilters = false)
class TeamChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TeamChatService teamChatService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @AfterEach
    void resetMocks() {
        reset(teamChatService, jwtService, messagingTemplate);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public TeamChatService teamChatService() {
            return mock(TeamChatService.class);
        }

        @Bean
        @Primary
        public JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        @Primary
        public SimpMessagingTemplate messagingTemplate() {
            return mock(SimpMessagingTemplate.class);
        }
    }

    @Test
    @DisplayName("GET /api/teams/{teamId}/chat/messages - Success")
    void getMessages_success() throws Exception {
        UUID teamId = UUID.randomUUID();
        String token = "valid-token";
        Long userId = 1L;

        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(teamChatService.getMessages(eq(teamId), eq(userId), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/teams/" + teamId + "/chat/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(teamChatService).getMessages(eq(teamId), eq(userId), any());
    }

    @Test
    @DisplayName("POST /api/teams/{teamId}/chat/messages - Success")
    void sendMessage_success() throws Exception {
        UUID teamId = UUID.randomUUID();
        String token = "valid-token";
        Long userId = 1L;

        TeamChatMessageRequest body = new TeamChatMessageRequest();
        body.setContent("Hello Team");
        body.setMessageType(TeamChatMessageRequest.MessageType.TEXT);

        TeamChatMessageResponse created = new TeamChatMessageResponse();
        created.setContent("Hello Team");

        when(jwtService.extractUserId(token)).thenReturn(userId);
        when(teamChatService.createMessage(eq(teamId), eq(userId), any())).thenReturn(created);

        mockMvc.perform(post("/api/teams/" + teamId + "/chat/messages")
                        .header("Authorization", "Bearer " + token)
                        .content(objectMapper.writeValueAsString(body))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(teamChatService).createMessage(eq(teamId), eq(userId), any());
        verify(messagingTemplate).convertAndSend(eq("/topic/teams/" + teamId + "/chat"), eq(created));
    }
}
