package com.docusphere.backend.team.document.controller;

import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.team.document.dto.DocumentSummaryDto;
import com.docusphere.backend.team.document.service.TeamDocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/team/{teamId}/documents")
public class TeamDocumentController {

    private final TeamDocumentService teamDocumentService;
    private final JwtService jwtService;

    public TeamDocumentController(TeamDocumentService teamDocumentService, JwtService jwtService) {
        this.teamDocumentService = teamDocumentService;
        this.jwtService = jwtService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentSummaryDto>>> getTeamDocuments(
            @PathVariable UUID teamId,
            @RequestHeader("Authorization") String token) {
        
        Long userId = jwtService.extractUserId(token.substring(7));
        List<DocumentSummaryDto> documents = teamDocumentService.getTeamDocuments(userId, teamId);
        return ResponseEntity.ok(ApiResponse.success("Team documents fetched successfully", documents));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponse<Void>> deleteTeamDocument(
            @PathVariable UUID teamId,
            @PathVariable UUID documentId,
            @RequestHeader("Authorization") String token) {
        
        Long userId = jwtService.extractUserId(token.substring(7));
        teamDocumentService.deleteTeamDocument(documentId, userId, teamId);
        return ResponseEntity.ok(ApiResponse.success("Document deleted successfully", null));
    }
}
