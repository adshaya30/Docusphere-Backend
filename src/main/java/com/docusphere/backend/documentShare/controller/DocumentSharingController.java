package com.docusphere.backend.documentShare.controller;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.documentShare.dto.CreateShareLinkRequest;
import com.docusphere.backend.documentShare.dto.CreateShareLinkResponse;
import com.docusphere.backend.documentShare.dto.SharedDocumentResponse;
import com.docusphere.backend.documentShare.service.DocumentSharingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
public class DocumentSharingController {

    private final DocumentSharingService documentSharingService;
    private final JwtService jwtService;

    public DocumentSharingController(DocumentSharingService documentSharingService, JwtService jwtService) {
        this.documentSharingService = documentSharingService;
        this.jwtService = jwtService;
    }

    @PostMapping("/api/documents/{id}/share")
    public ResponseEntity<ApiResponse<CreateShareLinkResponse>> createShareLink(
            @PathVariable("id") UUID documentId,
            @Valid @RequestBody CreateShareLinkRequest request,
            @RequestHeader("Authorization") String token
    ) {
        Long requesterId = extractRequesterId(token);
        CreateShareLinkResponse response = documentSharingService.createShareLink(requesterId, documentId, request);
        return ResponseEntity.ok(ApiResponse.success("Share link created successfully", response));
    }

    @DeleteMapping("/api/documents/{id}/share")
    public ResponseEntity<ApiResponse<Void>> revokeShareLink(
            @PathVariable("id") UUID documentId,
            @RequestParam("token") String shareToken,
            @RequestHeader("Authorization") String token
    ) {
        Long requesterId = extractRequesterId(token);
        documentSharingService.revokeShareLink(requesterId, documentId, shareToken);
        return ResponseEntity.ok(ApiResponse.success("Share link revoked successfully", null));
    }

    @GetMapping("/api/share/{token}")
    public ResponseEntity<ApiResponse<SharedDocumentResponse>> openSharedDocument(
            @PathVariable("token") String shareToken
    ) {
        SharedDocumentResponse response = documentSharingService.openSharedDocument(shareToken);
        return ResponseEntity.ok(ApiResponse.success("Shared document fetched successfully", response));
    }

    private Long extractRequesterId(String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            throw new InvalidRequestException("Authorization header with Bearer token is required");
        }
        return jwtService.extractUserId(token.substring(7));
    }
}
