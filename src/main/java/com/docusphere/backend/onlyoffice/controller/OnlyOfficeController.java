package com.docusphere.backend.onlyoffice.controller;

import com.docusphere.backend.Common.exception.AuthenticationRequiredException;
import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.audit.service.AuditService;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentShare.entity.DocumentShare;
import com.docusphere.backend.documentShare.service.DocumentSharingService;
import com.docusphere.backend.documentVersion.service.DocumentEditSaveService;
import com.docusphere.backend.documentVersion.service.DocumentVersionPermissionService;
import com.docusphere.backend.documentVersion.service.DocumentVersionService;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeCallback;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import com.docusphere.backend.onlyoffice.service.OnlyOfficeEditorConfigService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class OnlyOfficeController {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentVersionPermissionService permissionService;
    private final DocumentEditSaveService editSaveService;
    private final JwtService jwtService;
    private final OnlyOfficeEditorConfigService editorConfigService;
    private final DocumentVersionService documentVersionService;
    private final DocumentSharingService documentSharingService;
    private final AuditService auditService;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.onlyoffice.jwt.secret:V8pX9iu5gDWzQrHP5Od62XOOiuOnlrtF}")
    private String secretKey;

    @GetMapping("/api/editor/documents/{id}")
    public ResponseEntity<OnlyOfficeConfig> getEditorConfig(
            @PathVariable("id") UUID documentId,
            @RequestParam(value = "token", required = false) String shareToken,
            @RequestParam(value = "password", required = false) String password,
            @RequestHeader(value = "X-Document-Password", required = false) String passwordHeader,
            HttpServletRequest request
    ) {
        String effectivePassword = password != null && !password.isBlank() ? password : passwordHeader;
        OnlyOfficeConfig config = editorConfigService.buildEditorConfig(
                documentId, shareToken, effectivePassword, request);
        return ResponseEntity.ok(config);
    }

    @GetMapping("/api/editor/documents/{id}/versions/{versionId}")
    public ResponseEntity<OnlyOfficeConfig> getVersionPreviewConfig(
            @PathVariable("id") UUID documentId,
            @PathVariable("versionId") UUID versionId,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "token", required = false) String shareToken,
            HttpServletRequest request
    ) {
        Long requesterId = resolveOptionalRequesterId(request);
        if (requesterId == null && (shareToken == null || shareToken.isBlank())) {
            throw new AuthenticationRequiredException("Authorization token is required");
        }

        OnlyOfficeConfig config = documentVersionService.previewVersion(
                requesterId,
                documentId,
                versionId,
                password,
                shareToken
        );
        return ResponseEntity.ok(config);
    }

    @PostMapping("/api/editor/documents/{id}/forcesave")
    public ResponseEntity<ApiResponse<Map<String, String>>> forceSaveDocument(
            @PathVariable("id") UUID documentId,
            @RequestParam(value = "token", required = false) String shareToken,
            HttpServletRequest request
    ) {
        Long requesterId = resolveOptionalRequesterId(request);
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        if (shareToken != null && !shareToken.isBlank()) {
            documentSharingService.requireEditPermission(documentId, shareToken);
        } else {
            if (requesterId == null) {
                throw new AuthenticationRequiredException("Authorization token is required");
            }
            User user = userRepository.findById(requesterId)
                    .orElseThrow(() -> new InvalidRequestException("User not found"));
            boolean isAdmin = user.getRole() != null && "ROLE_ADMIN".equals(user.getRole().getName());
            if (!isAdmin && !permissionService.canEdit(document, requesterId, null)) {
                recordSecurityAudit("ONLYOFFICE_FORCE_SAVE_DENIED", documentId, requesterId, shareToken, null);
                throw new UnauthorizedAccessException("You do not have permission to save this document");
            }
        }

        return ResponseEntity.ok(
                ApiResponse.success(Map.of("status", "accepted", "documentId", documentId.toString()))
        );
    }

    @PostMapping("/api/onlyoffice/callback")
    public ResponseEntity<Map<String, Integer>> handleCallback(
            @RequestParam("id") UUID documentId,
            @RequestParam("token") String token,
            @RequestBody OnlyOfficeCallback callback,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        log.info("Received ONLYOFFICE callback for document ID: {} with status: {} and url: {}",
                documentId, callback.getStatus(), callback.getUrl());

        if ("preview_only".equals(token)) {
            return ResponseEntity.ok(Collections.singletonMap("error", 0));
        }

        if (!validateCallbackToken(token, documentId)) {
            recordSecurityAudit("ONLYOFFICE_CALLBACK_INVALID", documentId, null, null, null);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", 1));
        }

        if (callback.getStatus() != null && (callback.getStatus() == 2 || callback.getStatus() == 6)) {
            String downloadUrl = callback.getUrl();
            if (downloadUrl == null || downloadUrl.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Collections.singletonMap("error", 2));
            }

            try {
                String shareToken = extractShareTokenFromCallbackToken(token);
                Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                        .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

                Long editedBy = extractUserIdFromCallbackToken(token);
                String editorEmail = null;

                if (shareToken != null && !shareToken.isBlank()) {
                    DocumentShare share = documentSharingService.requireEditPermission(documentId, shareToken);
                    editorEmail = share.getEmail();
                    editedBy = document.getOwnerId();
                } else if (editedBy == null) {
                    editedBy = document.getOwnerId();
                }

                if (!canSaveViaCallback(document, editedBy, shareToken)) {
                    recordSecurityAudit("ONLYOFFICE_CALLBACK_SAVE_DENIED", documentId, editedBy, shareToken, null);
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                            .body(Collections.singletonMap("error", 1));
                }

                byte[] fileBytes = restTemplate.getForObject(downloadUrl, byte[].class);
                if (fileBytes == null || fileBytes.length == 0) {
                    throw new RuntimeException("Failed to download file from ONLYOFFICE");
                }

                String effectiveIdempotencyKey = resolveIdempotencyKey(idempotencyKey, callback);
                editSaveService.processOnlyOfficeSave(
                        documentId, editedBy, fileBytes, effectiveIdempotencyKey, editorEmail);
                recordSecurityAudit("ONLYOFFICE_CALLBACK_SAVE_ACCEPTED", documentId, editedBy, shareToken,
                        editorEmail != null ? Map.of("invitedEmail", editorEmail) : null);

            } catch (Exception e) {
                log.error("Failed to process ONLYOFFICE save callback for document ID: " + documentId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Collections.singletonMap("error", 3));
            }
        }

        return ResponseEntity.ok(Collections.singletonMap("error", 0));
    }

    private boolean canSaveViaCallback(Document document, Long editedBy, String shareToken) {
        if (shareToken != null && !shareToken.isBlank()) {
            return documentSharingService.hasEditPermissionViaShare(document.getId(), shareToken);
        }
        return permissionService.canEdit(document, editedBy, null);
    }

    private Long resolveOptionalRequesterId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return jwtService.extractUserId(authHeader.substring(7));
        }
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("accessToken".equalsIgnoreCase(cookie.getName())
                        || "jwt".equalsIgnoreCase(cookie.getName())) {
                    return jwtService.extractUserId(cookie.getValue());
                }
            }
        }
        return null;
    }

    private boolean validateCallbackToken(String token, UUID documentId) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String docIdStr = claims.get("documentId", String.class);
            return docIdStr != null && docIdStr.equals(documentId.toString());
        } catch (Exception e) {
            log.warn("Invalid ONLYOFFICE callback token for document {}: {}", documentId, e.getMessage());
            return false;
        }
    }

    private Long extractUserIdFromCallbackToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Object userIdClaim = claims.get("userId");
            if (userIdClaim instanceof Number number) {
                return number.longValue();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractShareTokenFromCallbackToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.get("shareToken", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveIdempotencyKey(String headerKey, OnlyOfficeCallback callback) {
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey.trim();
        }
        if (callback == null) {
            return null;
        }
        String key = callback.getKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.trim();
    }

    private void recordSecurityAudit(
            String action,
            UUID documentId,
            Long userId,
            String shareToken,
            Map<String, Object> extra
    ) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentId", documentId.toString());
        if (userId != null) {
            metadata.put("userId", userId);
        }
        if (shareToken != null && !shareToken.isBlank()) {
            metadata.put("shareToken", shareToken);
        }
        metadata.put("timestamp", LocalDateTime.now().toString());
        if (extra != null) {
            metadata.putAll(extra);
        }
        auditService.record(action, metadata);
    }
}
