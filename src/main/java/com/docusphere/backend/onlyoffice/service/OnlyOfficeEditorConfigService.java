package com.docusphere.backend.onlyoffice.service;

import com.docusphere.backend.Common.exception.AuthenticationRequiredException;
import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.audit.service.AuditService;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentProtection.service.DocumentPasswordProtectionService;
import com.docusphere.backend.documentShare.entity.DocumentShare;
import com.docusphere.backend.documentShare.service.DocumentSharingService;
import com.docusphere.backend.documentVersion.service.DocumentVersionPermissionService;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OnlyOfficeEditorConfigService {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentVersionPermissionService permissionService;
    private final JwtService jwtService;
    private final OnlyOfficeConfigBuilderService configBuilderService;
    private final DocumentSharingService documentSharingService;
    private final DocumentPasswordProtectionService documentPasswordProtectionService;
    private final OnlyOfficeDownloadTokenService downloadTokenService;
    private final AuditService auditService;

    @Value("${app.onlyoffice.jwt.secret:V8pX9iu5gDWzQrHP5Od62XOOiuOnlrtF}")
    private String secretKey;

    public OnlyOfficeConfig buildEditorConfig(
            UUID documentId,
            String shareToken,
            String password,
            HttpServletRequest request
    ) {
        String effectivePassword = resolvePassword(password, request != null ? request.getHeader("X-Document-Password") : null);
        Document document = documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        Long requesterId = request != null ? resolveOptionalRequesterId(request) : null;
        User user = resolveEditorUser(requesterId, shareToken, documentId);

        boolean isAdmin = requesterId != null && isAdminUser(requesterId);
        boolean canView = isAdmin || permissionService.canView(document, requesterId, shareToken);
        boolean canEdit = isAdmin || permissionService.canEdit(document, requesterId, shareToken);
        boolean canDownload = isAdmin || permissionService.canDownload(document, requesterId, shareToken);

        if (!canView) {
            recordSecurityAudit("ONLYOFFICE_VIEW_DENIED", documentId, requesterId, shareToken, null);
            throw new UnauthorizedAccessException("You do not have access to view this document");
        }

        if (canEdit && shareToken != null && !shareToken.isBlank()) {
            documentSharingService.requireEditPermission(documentId, shareToken);
            recordSecurityAudit("ONLYOFFICE_EDIT_SESSION_STARTED", documentId, requesterId, shareToken, null);
        } else if (canEdit) {
            recordSecurityAudit("ONLYOFFICE_EDIT_SESSION_STARTED", documentId, requesterId, shareToken, null);
        }

        documentPasswordProtectionService.requirePasswordForContentAccess(
                document,
                effectivePassword,
                requesterId,
                shareToken
        );

        String docKey = buildDocumentKey(documentId, document);
        String callbackToken = generateCallbackToken(documentId, requesterId, shareToken);
        String downloadToken = downloadTokenService.createDocumentDownloadToken(documentId, requesterId, shareToken);

        OnlyOfficeConfig config = configBuilderService.buildConfig(
                document,
                user,
                canEdit,
                canView,
                canDownload,
                docKey,
                callbackToken,
                downloadToken,
                shareToken
        );

        log.info("ONLYOFFICE editor config for document {} share={} url={}",
                documentId,
                shareToken != null && !shareToken.isBlank(),
                config.getDocument() != null ? config.getDocument().getUrl() : null);

        return config;
    }

    private String buildDocumentKey(UUID documentId, Document document) {
        long lastModified = document.getUpdatedAt() != null
                ? java.sql.Timestamp.valueOf(document.getUpdatedAt()).getTime()
                : (document.getCreatedAt() != null
                ? java.sql.Timestamp.valueOf(document.getCreatedAt()).getTime()
                : System.currentTimeMillis());

        String docKey = documentId + "_" + lastModified;
        docKey = docKey.replaceAll("[^a-zA-Z0-9_-]", "");
        if (docKey.length() > 20) {
            int hash = docKey.hashCode();
            docKey = "ds_" + documentId.toString().substring(0, 6) + "_" + Math.abs(hash) + "_v2";
        }
        return docKey;
    }

    private User resolveEditorUser(Long requesterId, String shareToken, UUID documentId) {
        if (shareToken != null && !shareToken.isBlank()) {
            DocumentShare share = documentSharingService.requireValidShareForDocument(documentId, shareToken);
            User guest = new User();
            guest.setId(0L);
            guest.setFullName(share.getEmail() != null ? share.getEmail() : "Guest");
            return guest;
        }

        if (requesterId != null) {
            return userRepository.findById(requesterId)
                    .orElseThrow(() -> new InvalidRequestException("User not found"));
        }

        throw new AuthenticationRequiredException("Authorization token is required");
    }

    private boolean isAdminUser(Long requesterId) {
        return userRepository.findById(requesterId)
                .map(user -> user.getRole() != null && "ROLE_ADMIN".equals(user.getRole().getName()))
                .orElse(false);
    }

    private Long resolveOptionalRequesterId(HttpServletRequest request) {
        String token = resolveAccessToken(request);
        if (token == null || token.isBlank()) {
            return null;
        }
        return jwtService.extractUserId(token);
    }

    private String resolveAccessToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
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

    private String resolvePassword(String password, String passwordHeader) {
        if (password != null && !password.isBlank()) {
            return password;
        }
        if (passwordHeader != null && !passwordHeader.isBlank()) {
            return passwordHeader;
        }
        return null;
    }

    private String generateCallbackToken(UUID documentId, Long userId, String shareToken) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("documentId", documentId.toString());
        if (userId != null) {
            claims.put("userId", userId);
        }
        if (shareToken != null && !shareToken.isBlank()) {
            claims.put("shareToken", shareToken.trim());
        }

        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 12 * 60 * 60 * 1000))
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .compact();
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
