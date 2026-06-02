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
import jakarta.servlet.http.HttpServletRequest;

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
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
        CreateShareLinkResponse response = documentSharingService.createShareLink(requesterId, documentId, request);
        return ResponseEntity.ok(ApiResponse.success("Share link created successfully", response));
    }

    @DeleteMapping("/api/documents/{id}/share")
    public ResponseEntity<ApiResponse<Void>> revokeShareLink(
            @PathVariable("id") UUID documentId,
            @RequestParam("token") String shareToken,
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
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

    private Long extractRequesterId(HttpServletRequest request) {
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

        if (token == null || token.isBlank()) {
            throw new InvalidRequestException("Authorization header or accessToken cookie is required");
        }

        return jwtService.extractUserId(token);
    }
}
