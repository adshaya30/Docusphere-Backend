package com.docusphere.backend.documentProtection.controller;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.documentProtection.dto.DocumentPasswordRequest;
import com.docusphere.backend.documentProtection.dto.DocumentProtectionResponse;
import com.docusphere.backend.documentProtection.dto.PasswordVerificationResponse;
import com.docusphere.backend.documentProtection.dto.ResetDocumentPasswordRequest;
import com.docusphere.backend.documentProtection.service.DocumentPasswordProtectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@Validated
public class DocumentProtectionController {

    private final DocumentPasswordProtectionService protectionService;
    private final JwtService jwtService;

    public DocumentProtectionController(
            DocumentPasswordProtectionService protectionService,
            JwtService jwtService
    ) {
        this.protectionService = protectionService;
        this.jwtService = jwtService;
    }

    @PostMapping("/{id}/protect")
    public ResponseEntity<ApiResponse<DocumentProtectionResponse>> enableProtection(
            @PathVariable("id") UUID documentId,
            @Valid @RequestBody DocumentPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
        DocumentProtectionResponse response = protectionService.enableProtection(
                requesterId,
                documentId,
                request.getPassword()
        );
        return ResponseEntity.ok(ApiResponse.success("Document protection enabled", response));
    }

    @PostMapping("/{id}/protect/reset")
    public ResponseEntity<ApiResponse<DocumentProtectionResponse>> resetProtection(
            @PathVariable("id") UUID documentId,
            @Valid @RequestBody ResetDocumentPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
        DocumentProtectionResponse response = protectionService.resetProtectionPassword(
                requesterId,
                documentId,
                request.getNewPassword()
        );
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully", response));
    }

    @DeleteMapping("/{id}/protect")
    public ResponseEntity<ApiResponse<DocumentProtectionResponse>> removeProtection(
            @PathVariable("id") UUID documentId,
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
        DocumentProtectionResponse response = protectionService.removeProtection(requesterId, documentId);
        return ResponseEntity.ok(ApiResponse.success("Document protection removed", response));
    }

    @PostMapping("/{id}/verify-password")
    public ResponseEntity<ApiResponse<PasswordVerificationResponse>> verifyPassword(
            @PathVariable("id") UUID documentId,
            @Valid @RequestBody DocumentPasswordRequest request,
            HttpServletRequest httpRequest,
            @RequestParam(value = "token", required = false) String shareToken
    ) {
        Long requesterId = null;
        try {
            requesterId = extractRequesterId(httpRequest);
        } catch (Exception ignored) {
            // Optional if shareToken is present
        }

        if (requesterId == null && (shareToken == null || shareToken.isBlank())) {
            throw new InvalidRequestException("Authorization or share token is required");
        }

        PasswordVerificationResponse response = protectionService.verifyPassword(
                requesterId,
                documentId,
                request.getPassword(),
                shareToken
        );
        return ResponseEntity.ok(ApiResponse.success("Password verified", response));
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
