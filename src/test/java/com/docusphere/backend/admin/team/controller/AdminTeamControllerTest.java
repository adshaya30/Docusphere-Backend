package com.docusphere.backend.admin.team.controller;

import com.docusphere.backend.admin.team.dto.AdminCreateTeamRequest;
import com.docusphere.backend.admin.team.dto.AdminMemberView;
import com.docusphere.backend.admin.team.dto.MergeTeamsRequest;
import com.docusphere.backend.admin.team.service.*;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.team.dto.AddMemberRequest;
import com.docusphere.backend.team.dto.TeamDto;
import com.docusphere.backend.team.dto.TransferLeaderRequest;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.service.TeamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.security.core.userdetails.UserDetailsService;

@WebMvcTest(controllers = AdminTeamController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminTeamController Unit Tests")
class AdminTeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean private AdminTeamCoreService coreService;
    @MockitoBean private AdminTeamQueryService queryService;
    @MockitoBean private AdminTeamMemberService memberService;
    @MockitoBean private AdminTeamMergeService mergeService;
    @MockitoBean private AdminTeamDocumentService documentService;
    @MockitoBean private TeamService teamService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    @DisplayName("Should return all teams")
    void testGetAllTeams_Success() throws Exception {
        TeamDto dto = new TeamDto();
        dto.setName("Alpha");

        when(teamService.getAllTeams()).thenReturn(Collections.singletonList(dto));

        mockMvc.perform(get("/api/admin/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alpha"));
    }

    @Test
    @DisplayName("Should return team by ID")
    void testGetTeamById_Success() throws Exception {
        UUID teamId = UUID.randomUUID();
        Team team = new Team();
        team.setId(teamId);
        team.setTeamName("Beta");

        TeamDto dto = new TeamDto();
        dto.setName("Beta");

        when(teamService.findTeamById(teamId)).thenReturn(Optional.of(team));
        when(teamService.toDto(team)).thenReturn(dto);

        mockMvc.perform(get("/api/admin/teams/{teamId}", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Beta"));
    }

    @Test
    @DisplayName("Should return 404 when team is not found by ID")
    void testGetTeamById_NotFound() throws Exception {
        UUID teamId = UUID.randomUUID();
        when(teamService.findTeamById(teamId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/teams/{teamId}", teamId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should create team successfully")
    void testCreateTeam_Success() throws Exception {
        AdminCreateTeamRequest request = new AdminCreateTeamRequest();
        request.setName("Gamma Team");

        TeamDto responseDto = new TeamDto();
        responseDto.setName("Gamma Team");

        when(coreService.createTeam(any(AdminCreateTeamRequest.class))).thenReturn(responseDto);

        mockMvc.perform(post("/api/admin/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Gamma Team"));
    }

    @Test
    @DisplayName("Should delete team successfully")
    void testDeleteTeam_Success() throws Exception {
        UUID teamId = UUID.randomUUID();
        doNothing().when(coreService).deleteTeam(teamId);

        mockMvc.perform(delete("/api/admin/teams/{teamId}", teamId))
                .andExpect(status().isNoContent());

        verify(coreService, times(1)).deleteTeam(teamId);
    }

    @Test
    @DisplayName("Should return team members")
    void testGetMembers_Success() throws Exception {
        UUID teamId = UUID.randomUUID();
        AdminMemberView view = new AdminMemberView();
        view.setFullName("Captain Marvel");

        when(queryService.getTeamMembers(teamId)).thenReturn(Collections.singletonList(view));

        mockMvc.perform(get("/api/admin/teams/{teamId}/members", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Captain Marvel"));
    }

    @Test
    @DisplayName("Should add a member successfully")
    void testAddMember_Success() throws Exception {
        UUID teamId = UUID.randomUUID();
        AddMemberRequest request = new AddMemberRequest();
        request.setEmail("new@docusphere.com");
        request.setRole("MEMBER");

        AdminMemberView response = new AdminMemberView();
        response.setEmail("new@docusphere.com");

        when(memberService.addMember(eq(teamId), any(AddMemberRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/admin/teams/{teamId}/members", teamId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@docusphere.com"));
    }

    @Test
    @DisplayName("Should delete a member successfully")
    void testDeleteMember_Success() throws Exception {
        UUID teamId = UUID.randomUUID();
        Long userId = 100L;

        doNothing().when(memberService).deleteMember(teamId, userId);

        mockMvc.perform(delete("/api/admin/teams/{teamId}/members/{userId}", teamId, userId))
                .andExpect(status().isNoContent());

        verify(memberService, times(1)).deleteMember(teamId, userId);
    }

    @Test
    @DisplayName("Should update member role successfully")
    void testUpdateMemberRole_Success() throws Exception {
        UUID teamId = UUID.randomUUID();
        Long userId = 100L;
        AddMemberRequest request = new AddMemberRequest();
        request.setRole("MANAGER");

        doNothing().when(memberService).updateMemberRole(teamId, userId, "MANAGER");

        mockMvc.perform(put("/api/admin/teams/{teamId}/members/{userId}/role", teamId, userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should transfer leadership successfully")
    void testTransferLeader_Success() throws Exception {
        UUID teamId = UUID.randomUUID();
        TransferLeaderRequest request = new TransferLeaderRequest();
        request.setNewLeaderId(200L);

        doNothing().when(memberService).transferLeader(eq(teamId), any(TransferLeaderRequest.class));

        mockMvc.perform(post("/api/admin/teams/{teamId}/transfer-leader", teamId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return team documents")
    void testGetDocuments_Success() throws Exception {
        UUID teamId = UUID.randomUUID();
        com.docusphere.backend.team.document.dto.DocumentSummaryDto docDto =
                com.docusphere.backend.team.document.dto.DocumentSummaryDto.builder()
                        .name("Document Name")
                        .build();

        when(documentService.getTeamDocuments(teamId)).thenReturn(Collections.singletonList(docDto));

        mockMvc.perform(get("/api/admin/teams/{teamId}/documents", teamId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Document Name"));
    }

    @Test
    @DisplayName("Should delete a document permanently")
    void testDeleteDocument_Success() throws Exception {
        UUID teamId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        doNothing().when(documentService).deleteDocumentPermanently(teamId, documentId);

        mockMvc.perform(delete("/api/admin/teams/{teamId}/documents/{documentId}", teamId, documentId))
                .andExpect(status().isNoContent());

        verify(documentService, times(1)).deleteDocumentPermanently(teamId, documentId);
    }

    @Test
    @DisplayName("Should merge teams successfully")
    void testMergeTeams_Success() throws Exception {
        MergeTeamsRequest request = new MergeTeamsRequest();
        request.setSourceTeamId(UUID.randomUUID());
        request.setTargetTeamId(UUID.randomUUID());
        request.setNewTeamName("Merged Team Name");
        request.setNewLeaderId(300L);
        request.setMoveDocuments(true);

        Team mergedTeam = new Team();
        mergedTeam.setTeamName("Merged Team Name");
        TeamDto mergedDto = new TeamDto();
        mergedDto.setName("Merged Team Name");

        when(mergeService.mergeTeams(any(), any(), any(), any(), anyBoolean())).thenReturn(mergedTeam);
        when(teamService.toDto(mergedTeam)).thenReturn(mergedDto);

        mockMvc.perform(post("/api/admin/teams/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Merged Team Name"));
    }
}
