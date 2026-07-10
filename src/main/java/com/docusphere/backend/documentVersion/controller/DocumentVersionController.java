package com.docusphere.backend.documentVersion.controller;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.documentVersion.dto.CreateVersionRequest;
import com.docusphere.backend.documentVersion.dto.DocumentVersionListResponse;
import com.docusphere.backend.documentVersion.dto.DocumentVersionResponse;
import com.docusphere.backend.documentVersion.dto.RestoreVersionResponse;
import com.docusphere.backend.documentVersion.dto.SaveChangeSummaryResponse;
import com.docusphere.backend.documentVersion.service.DocumentVersionService;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents/{documentId}/versions")
@RequiredArgsConstructor
public class DocumentVersionController {

    private final DocumentVersionService documentVersionService;
    private final JwtService jwtService;

    @Value("${app.onlyoffice.jwt.secret:V8pX9iu5gDWzQrHP5Od62XOOiuOnlrtF}")
    private String onlyofficeJwtSecret;

    @GetMapping
    public ResponseEntity<ApiResponse<DocumentVersionListResponse>> listVersions(
            @PathVariable UUID documentId,
            HttpServletRequest request
    ) {
        Long requesterId = extractRequesterId(request);
        DocumentVersionListResponse data = documentVersionService.listVersions(requesterId, documentId);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping("/summary")
    public ResponseEntity<ApiResponse<SaveChangeSummaryResponse>> saveChangeSummary(
            @PathVariable UUID documentId,
            @RequestParam(value = "token", required = false) String shareToken,
            @RequestBody(required = false) CreateVersionRequest body,
            HttpServletRequest request
    ) {
        String changeSummary = body != null ? body.resolveSummary() : null;
        SaveChangeSummaryResponse data;
        if (shareToken != null && !shareToken.isBlank()) {
            data = documentVersionService.saveChangeSummaryForShare(shareToken, documentId, changeSummary);
        } else {
            Long requesterId = extractRequesterId(request);
            data = documentVersionService.saveChangeSummary(requesterId, documentId, changeSummary);
        }
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/{versionId}")
    public ResponseEntity<ApiResponse<DocumentVersionResponse>> getVersion(
            @PathVariable UUID documentId,
            @PathVariable UUID versionId,
            HttpServletRequest request
    ) {
        Long requesterId = extractRequesterId(request);
        DocumentVersionResponse data = documentVersionService.getVersion(requesterId, documentId, versionId);
        return ResponseEntity.ok(ApiResponse.success("Version metadata fetched successfully", data));
    }

    @GetMapping("/{versionId}/preview")
    public ResponseEntity<OnlyOfficeConfig> previewVersion(
            @PathVariable UUID documentId,
            @PathVariable UUID versionId,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "token", required = false) String shareToken,
            @RequestHeader(value = "X-Document-Password", required = false) String passwordHeader,
            HttpServletRequest request
    ) {
        Long requesterId = extractRequesterId(request);
        OnlyOfficeConfig config = documentVersionService.previewVersion(
                requesterId,
                documentId,
                versionId,
                resolvePassword(password, passwordHeader),
                shareToken
        );
        return ResponseEntity.ok(config);
    }

    @GetMapping("/{versionId}/download")
    public ResponseEntity<Resource> downloadVersion(
            @PathVariable UUID documentId,
            @PathVariable UUID versionId,
            @RequestParam(value = "dlToken", required = false) String dlToken,
            HttpServletRequest request
    ) {
        if (dlToken != null && !dlToken.isBlank()) {
            Resource resource = documentVersionService.downloadVersionForSystem(documentId, versionId, dlToken);
            String fileName = documentVersionService.resolveDownloadFilename(null, documentId, versionId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .body(resource);
        }

        Long requesterId = extractRequesterId(request);
        Resource resource = documentVersionService.downloadVersion(requesterId, documentId, versionId, null, null);
        String fileName = documentVersionService.resolveDownloadFilename(requesterId, documentId, versionId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @PostMapping("/{versionId}/restore")
    public ResponseEntity<ApiResponse<RestoreVersionResponse>> restoreVersion(
            @PathVariable UUID documentId,
            @PathVariable UUID versionId,
            HttpServletRequest request
    ) {
        Long requesterId = extractRequesterId(request);
        RestoreVersionResponse data = documentVersionService.restoreVersion(requesterId, documentId, versionId);
        return ResponseEntity.ok(ApiResponse.success("Version restored successfully", data));
    }

    private String resolvePassword(String password, String passwordHeader) {
        if (password != null && !password.isBlank()) {
            return password;
        }
        if (passwordHeader != null && !passwordHeader.isBlank()) {
            return passwordHeader;
        }
        return null;
    }

    private Long extractRequesterId(HttpServletRequest request) {
        String token = resolveAccessToken(request);
        if (token == null || token.isBlank()) {
            throw new InvalidRequestException("Authorization header or accessToken cookie is required");
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

    private boolean isOnlyOfficeRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                io.jsonwebtoken.Jwts.parser()
                        .verifyWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(onlyofficeJwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .build()
                        .parseSignedClaims(token);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }
}