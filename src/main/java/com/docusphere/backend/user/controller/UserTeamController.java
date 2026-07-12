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
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.team.document.dto.DocumentSummaryDto;
import com.docusphere.backend.team.dto.AddMemberRequest;
import com.docusphere.backend.team.dto.TeamDto;
import com.docusphere.backend.team.dto.TeamMemberDto;
import com.docusphere.backend.team.dto.UserStatusDto;
import com.docusphere.backend.team.service.TeamService;
import com.docusphere.backend.user.services.UserDocumentService;
import com.docusphere.backend.user.services.UserTeamService;

@RestController
@RequestMapping("/api/teams")
public class UserTeamController {

    private final UserTeamService userTeamService;
    private final TeamService teamService;
    private final UserDocumentService userDocumentService;
    private final JwtService jwtService;

    public UserTeamController(UserTeamService userTeamService,
                              TeamService teamService,
                              UserDocumentService userDocumentService,
                              JwtService jwtService) {
        this.userTeamService = userTeamService;
        this.teamService = teamService;
        this.userDocumentService = userDocumentService;
        this.jwtService = jwtService;
    }

    private Long getUserId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                if ("accessToken".equalsIgnoreCase(c.getName()) || "jwt".equalsIgnoreCase(c.getName()) || "Authorization".equalsIgnoreCase(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }

        if (token != null && !token.isBlank()) {
            return jwtService.extractUserId(token);
        }

        throw new IllegalArgumentException("Invalid or missing Authorization token or accessToken cookie");
    }

    @GetMapping
    public ResponseEntity<List<TeamDto>> getMyTeams(HttpServletRequest request) {
        Long userId = getUserId(request);
        
        // Auto-link pending invitations before returning teams
        userTeamService.processPendingInvitations(userId);

        // We find all teams this user belongs to
        List<TeamDto> myTeams = teamService.getAllTeams().stream()
                .filter(team -> teamService.isUserInTeam(userId, team.getId()))
            .peek(team -> teamService.findMembership(userId, team.getId())
                .ifPresent(m -> team.setCurrentUserRole(m.getRole() != null ? m.getRole().name() : null)))
                .collect(Collectors.toList());
        return ResponseEntity.ok(myTeams);
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamDto> getTeamById(@PathVariable UUID teamId, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (!teamService.isUserInTeam(userId, teamId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return teamService.findTeamById(teamId)
                .map(team -> ResponseEntity.ok(teamService.toDto(team)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{teamId}/members")
    public ResponseEntity<List<TeamMemberDto>> getTeamMembers(@PathVariable UUID teamId, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (!teamService.isUserInTeam(userId, teamId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(teamService.getMembersOfTeam(teamId));
    }

    @GetMapping("/{teamId}/members/status")
    public ResponseEntity<List<UserStatusDto>> getTeamMemberStatuses(@PathVariable UUID teamId, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (!teamService.isUserInTeam(userId, teamId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userTeamService.touchMemberLastSeen(teamId, userId);
        return ResponseEntity.ok(teamService.getTeamMemberStatuses(teamId));
    }

    @PostMapping("/{teamId}/presence")
    public ResponseEntity<Void> recordTeamPresence(@PathVariable UUID teamId, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (!teamService.isUserInTeam(userId, teamId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userTeamService.recordTeamPageAccess(teamId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{teamId}/documents")
    public ResponseEntity<List<DocumentSummaryDto>> getTeamDocuments(@PathVariable UUID teamId, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (!teamService.isUserInTeam(userId, teamId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(userDocumentService.getAccessibleDocuments(userId, teamId));
    }

    @PostMapping
    public ResponseEntity<TeamDto> createTeam(@RequestBody TeamDto request, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        TeamDto created = userTeamService.createTeam(request.getName(), request.getDescription(), request.getMembers(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> deleteTeam(@PathVariable UUID teamId, HttpServletRequest request) {
        Long userId = getUserId(request);
        userTeamService.deleteTeam(teamId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{teamId}/members")
    public ResponseEntity<TeamMemberDto> addMember(@PathVariable UUID teamId, @RequestBody AddMemberRequest request, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        TeamMemberDto added = userTeamService.addMember(teamId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(added);
    }

    @DeleteMapping("/{teamId}/members/{memberId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID teamId, @PathVariable Long memberId, HttpServletRequest request) {
        Long userId = getUserId(request);
        userTeamService.removeMember(teamId, memberId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{teamId}/members/{memberId}/role")
    public ResponseEntity<Void> updateMemberRole(@PathVariable UUID teamId, @PathVariable Long memberId, @RequestBody AddMemberRequest request, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        userTeamService.updateMemberRole(teamId, memberId, request.getRole(), userId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{teamId}/members/{memberId}/chat-block")
    public ResponseEntity<Void> updateMemberChatBlock(
            @PathVariable UUID teamId,
            @PathVariable Long memberId,
            @RequestParam boolean blocked,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        if (!teamService.isUserInTeam(userId, teamId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userTeamService.setMemberChatAccess(teamId, memberId, blocked, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{teamId}/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID teamId, @PathVariable UUID documentId, HttpServletRequest request) {
        Long userId = getUserId(request);
        userDocumentService.deleteDocument(documentId, userId, teamId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public ResponseEntity<Void> acceptInvitation(@PathVariable UUID invitationId, HttpServletRequest request) {
        Long userId = getUserId(request);
        userTeamService.acceptInvitation(invitationId, userId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/invitations/{invitationId}/decline")
    public ResponseEntity<Void> declineInvitation(@PathVariable UUID invitationId, HttpServletRequest request) {
        Long userId = getUserId(request);
        userTeamService.declineInvitation(invitationId, userId);
        return ResponseEntity.ok().build();
    }
}
