package com.docusphere.backend.documentProtection.controller;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.documentProtection.dto.DocumentPasswordRequest;
import com.docusphere.backend.documentProtection.dto.DocumentProtectionResponse;
import com.docusphere.backend.documentProtection.dto.PasswordVerificationResponse;
import com.docusphere.backend.documentProtection.dto.ResetDocumentPasswordRequest;
import com.docusphere.backend.documentProtection.service.DocumentPasswordProtectionService;
import jakarta.servlet.http.Cookie;
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

    @PostMapping({"/{id}/protect/reset", "/{id}/password/reset"})
    public ResponseEntity<ApiResponse<DocumentProtectionResponse>> resetProtection(
            @PathVariable("id") UUID documentId,
            @Valid @RequestBody ResetDocumentPasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
        DocumentProtectionResponse response = protectionService.resetProtectionPassword(
                requesterId,
                documentId,
                request.getNewPassword(),
                request.getAccountPassword()
        );
        return ResponseEntity.ok(ApiResponse.success("Document protection password reset successfully", response));
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
            @RequestParam(value = "token", required = false) String shareToken,
            HttpServletRequest httpRequest
    ) {
        Long requesterId = resolveOptionalRequesterId(httpRequest);
        if (requesterId == null && (shareToken == null || shareToken.isBlank())) {
            throw new InvalidRequestException("Authorization, accessToken cookie, or share token is required");
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
        String token = resolveAccessToken(request);
        if (token == null || token.isBlank()) {
            throw new InvalidRequestException("Authorization header or accessToken cookie is required");
        }
        return jwtService.extractUserId(token);
    }

    private Long resolveOptionalRequesterId(HttpServletRequest request) {
        String token = resolveAccessToken(request);
        if (token == null || token.isBlank()) {
            return null;
        }
        return jwtService.extractUserId(token);
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if ("accessToken".equalsIgnoreCase(cookie.getName())
                    || "jwt".equalsIgnoreCase(cookie.getName())
                    || "Authorization".equalsIgnoreCase(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
