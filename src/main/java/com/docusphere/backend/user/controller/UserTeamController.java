package com.docusphere.backend.user.controller;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.team.document.dto.DocumentSummaryDto;
import com.docusphere.backend.team.dto.AddMemberRequest;
import com.docusphere.backend.team.dto.TeamDto;
import com.docusphere.backend.team.dto.TeamMemberDto;
import com.docusphere.backend.team.dto.UserStatusDto;
import com.docusphere.backend.team.service.TeamService;
import com.docusphere.backend.team.document.service.TeamDocumentService;
import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.user.services.UserTeamService;

@RestController
@RequestMapping("/api/teams")
public class UserTeamController {

    private final UserTeamService userTeamService;
    private final TeamService teamService;
    private final TeamDocumentService teamDocumentService;
    private final JwtService jwtService;

    public UserTeamController(UserTeamService userTeamService,
                              TeamService teamService,
                              TeamDocumentService teamDocumentService,
                              JwtService jwtService) {
        this.userTeamService = userTeamService;
        this.teamService = teamService;
        this.teamDocumentService = teamDocumentService;
        this.jwtService = jwtService;
    }

    private Long getUserId(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return jwtService.extractUserId(token.substring(7));
        }
        throw new IllegalArgumentException("Invalid or missing Authorization header");
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<TeamDto>>> getMyTeams(@RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        
        // Auto-link pending invitations before returning teams
        userTeamService.processPendingInvitations(userId);

        // We find all teams this user belongs to
        List<TeamDto> myTeams = teamService.getAllTeams().stream()
                .filter(team -> teamService.isUserInTeam(userId, team.getId()))
            .peek(team -> teamService.findMembership(userId, team.getId())
                .ifPresent(m -> team.setCurrentUserRole(m.getRole() != null ? m.getRole().name() : null)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success("Teams fetched successfully", myTeams));
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<ApiResponse<TeamDto>> getTeamById(@PathVariable UUID teamId, @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        if (!teamService.isUserInTeam(userId, teamId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return teamService.findTeamById(teamId)
                .map(team -> ResponseEntity.ok(ApiResponse.success("Team fetched successfully", teamService.toDto(team))))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{teamId}/members")
    public ResponseEntity<ApiResponse<List<TeamMemberDto>>> getTeamMembers(@PathVariable UUID teamId, @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        if (!teamService.isUserInTeam(userId, teamId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ApiResponse.success("Members fetched successfully", teamService.getMembersOfTeam(teamId)));
    }

    @GetMapping("/{teamId}/members/status")
    public ResponseEntity<ApiResponse<List<UserStatusDto>>> getTeamMemberStatuses(@PathVariable UUID teamId, @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        if (!teamService.isUserInTeam(userId, teamId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ApiResponse.success("Member statuses fetched successfully", teamService.getTeamMemberStatuses(teamId)));
    }

    @GetMapping("/{teamId}/documents")
    public ResponseEntity<ApiResponse<List<DocumentSummaryDto>>> getTeamDocuments(@PathVariable UUID teamId, @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        if (!teamService.isUserInTeam(userId, teamId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(ApiResponse.success("Documents fetched successfully", teamDocumentService.getTeamDocuments(userId, teamId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TeamDto>> createTeam(@jakarta.validation.Valid @RequestBody TeamDto request, @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        TeamDto created = userTeamService.createTeam(request.getName(), request.getDescription(), request.getMembers(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Team created successfully", created));
    }

    @DeleteMapping("/{teamId}")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(@PathVariable UUID teamId, @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        userTeamService.deleteTeam(teamId, userId);
        return ResponseEntity.ok(ApiResponse.success("Team deleted successfully", null));
    }

    @PostMapping("/{teamId}/members")
    public ResponseEntity<ApiResponse<TeamMemberDto>> addMember(@PathVariable UUID teamId, @jakarta.validation.Valid @RequestBody AddMemberRequest request, @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        TeamMemberDto added = userTeamService.addMember(teamId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Member added successfully", added));
    }

    @DeleteMapping("/{teamId}/members/{memberId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(@PathVariable UUID teamId, @PathVariable Long memberId, @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        userTeamService.removeMember(teamId, memberId, userId);
        return ResponseEntity.ok(ApiResponse.success("Member removed successfully", null));
    }

    @PutMapping("/{teamId}/members/{memberId}/role")
    public ResponseEntity<ApiResponse<Void>> updateMemberRole(@PathVariable UUID teamId, @PathVariable Long memberId, @jakarta.validation.Valid @RequestBody AddMemberRequest request, @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        userTeamService.updateMemberRole(teamId, memberId, request.getRole(), userId);
        return ResponseEntity.ok(ApiResponse.success("Member role updated successfully", null));
    }

    @DeleteMapping("/{teamId}/documents/{documentId}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID teamId, @PathVariable UUID documentId, @RequestHeader("Authorization") String token) {
        Long userId = getUserId(token);
        teamDocumentService.deleteTeamDocument(documentId, userId, teamId);
        return ResponseEntity.ok(ApiResponse.success("Document deleted successfully", null));
    }
}
