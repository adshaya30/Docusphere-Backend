package com.docusphere.backend.admin.team.controller;

import com.docusphere.backend.admin.team.dto.AdminCreateTeamRequest;
import com.docusphere.backend.admin.team.dto.AdminMemberView;
import com.docusphere.backend.admin.team.dto.MergeTeamsRequest;
import com.docusphere.backend.admin.team.service.*;
import com.docusphere.backend.team.dto.AddMemberRequest;
import com.docusphere.backend.team.dto.TeamDto;
import com.docusphere.backend.team.dto.TransferLeaderRequest;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.service.TeamService;
import jakarta.validation.Valid;


import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/teams")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTeamController {

    private final AdminTeamCoreService coreService;
    private final AdminTeamQueryService queryService;
    private final AdminTeamMemberService memberService;
    private final AdminTeamMergeService mergeService;
    private final AdminTeamDocumentService documentService;
    private final TeamService teamService;
    public AdminTeamController(AdminTeamCoreService coreService,
            AdminTeamQueryService queryService,
            AdminTeamMemberService memberService,
            AdminTeamMergeService mergeService,
            AdminTeamDocumentService documentService,
            TeamService teamService) {
        this.coreService = coreService;
        this.queryService = queryService;
        this.memberService = memberService;
        this.mergeService = mergeService;
        this.documentService = documentService;
        this.teamService = teamService;
    }

    @GetMapping
    public ResponseEntity<List<TeamDto>> getAllTeams() {
        return ResponseEntity.ok(teamService.getAllTeams());
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamDto> getTeamById(@PathVariable UUID teamId) {
        return teamService.findTeamById(teamId)
                .map(t -> ResponseEntity.ok(teamService.toDto(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TeamDto> createTeam(@Valid @RequestBody AdminCreateTeamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(coreService.createTeam(request));
    }

    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> deleteTeam(@PathVariable UUID teamId) {
        coreService.deleteTeam(teamId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{teamId}/members")
    public ResponseEntity<List<AdminMemberView>> getMembers(@PathVariable UUID teamId) {
        return ResponseEntity.ok(queryService.getTeamMembers(teamId));
    }

    @PostMapping("/{teamId}/members")
    public ResponseEntity<AdminMemberView> addMember(@PathVariable UUID teamId,
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(memberService.addMember(teamId, request));
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<Void> deleteMember(@PathVariable UUID teamId, @PathVariable Long userId) {
        memberService.deleteMember(teamId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{teamId}/members/{userId}/role")
    public ResponseEntity<Void> updateMemberRole(@PathVariable UUID teamId, @PathVariable Long userId,
            @Valid @RequestBody AddMemberRequest request) {
        memberService.updateMemberRole(teamId, userId, request.getRole());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{teamId}/transfer-leader")
    public ResponseEntity<Void> transferLeader(@PathVariable UUID teamId,
            @Valid @RequestBody TransferLeaderRequest request) {
        memberService.transferLeader(teamId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{teamId}/documents")
    public ResponseEntity<List<com.docusphere.backend.team.document.dto.DocumentSummaryDto>> getDocuments(
            @PathVariable UUID teamId) {
        return ResponseEntity.ok(documentService.getTeamDocuments(teamId));
    }

    // add the code need to be like this format
    @DeleteMapping("/{teamId}/documents/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID teamId, @PathVariable UUID documentId) {
        documentService.deleteDocumentPermanently(teamId, documentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/merge")
    public ResponseEntity<TeamDto> mergeTeams(@Valid @RequestBody MergeTeamsRequest request) {
        Team mergedTeam = mergeService.mergeTeams(
                request.getSourceTeamId(), request.getTargetTeamId(),
                request.getNewTeamName(), request.getNewLeaderId(),
                request.isMoveDocuments());
        return ResponseEntity.ok(teamService.toDto(mergedTeam));
    }

}
