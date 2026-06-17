package com.docusphere.backend.onlyoffice.controller;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeCallback;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import com.docusphere.backend.onlyoffice.service.DocumentEditPermissionService;
import com.docusphere.backend.onlyoffice.service.OnlyOfficeConfigBuilderService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class OnlyOfficeController {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentEditPermissionService permissionService;
    private final FileStorageService fileStorageService;
    private final JwtService jwtService;
    private final OnlyOfficeConfigBuilderService configBuilderService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.onlyoffice.callback-url:http://localhost:8080/api/onlyoffice/callback}")
    private String onlyofficeCallbackUrl;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service.key}")
    private String serviceKey;

    @Value("${supabase.bucket.documents:documents}")
    private String bucketName;

    /**
     * GET /api/editor/documents/{id}
     * Purpose: Return ONLYOFFICE document configuration.
     */
    @GetMapping("/api/editor/documents/{id}")
    public ResponseEntity<OnlyOfficeConfig> getEditorConfig(
            @PathVariable("id") UUID documentId,
            HttpServletRequest request
    ) {
        Long requesterId = extractRequesterId(request);
        
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        User user = userRepository.findById(requesterId)
                .orElseThrow(() -> new InvalidRequestException("User not found"));

        boolean isAdmin = user.getRole() != null && "ROLE_ADMIN".equals(user.getRole().getName());

        if (!isAdmin && !permissionService.canView(document, requesterId)) {
            throw new UnauthorizedAccessException("You do not have access to view this document");
        }

        boolean canEdit = isAdmin || permissionService.canEdit(document, requesterId);
        boolean canView = isAdmin || permissionService.canView(document, requesterId);
        boolean canDownload = isAdmin || permissionService.canDownload(document, requesterId);

        long lastModified = document.getUpdatedAt() != null 
                ? java.sql.Timestamp.valueOf(document.getUpdatedAt()).getTime() 
                : (document.getCreatedAt() != null ? java.sql.Timestamp.valueOf(document.getCreatedAt()).getTime() : System.currentTimeMillis());

        String docKey = documentId.toString() + "_" + lastModified;
        // ONLYOFFICE key parameter must be maximum 20 characters in older versions, 
        // but can be longer. Let's make sure it contains only valid characters.
        docKey = docKey.replaceAll("[^a-zA-Z0-9_-]", "");
        if (docKey.length() > 20) {
            // ONLYOFFICE recommendation: do not exceed 20 characters in old Community Editions. 
            // We can hash it if it's too long or use the timestamp hash to fit.
            int hash = docKey.hashCode();
            docKey = documentId.toString().substring(0, 8) + "_" + Math.abs(hash);
        }

        String callbackToken = generateCallbackToken(documentId, requesterId);

        OnlyOfficeConfig config = configBuilderService.buildConfig(
                document,
                user,
                canEdit,
                canView,
                canDownload,
                docKey,
                callbackToken
        );

        return ResponseEntity.ok(config);
    }

    /**
     * POST /api/onlyoffice/callback
     * Purpose: Receive save callback from ONLYOFFICE.
     */
    @PostMapping("/api/onlyoffice/callback")
    public ResponseEntity<Map<String, Integer>> handleCallback(
            @RequestParam("id") UUID documentId,
            @RequestParam("token") String token,
            @RequestBody OnlyOfficeCallback callback
    ) {
        log.info("Received ONLYOFFICE callback for document ID: {} with status: {} and url: {}", documentId, callback.getStatus(), callback.getUrl());

        if (!validateCallbackToken(token, documentId)) {
            log.error("Invalid ONLYOFFICE callback token for document ID: {}", documentId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Collections.singletonMap("error", 1));
        }

        // Status 2 is ready for save, Status 6 is force save
        if (callback.getStatus() != null && (callback.getStatus() == 2 || callback.getStatus() == 6)) {
            String downloadUrl = callback.getUrl();
            if (downloadUrl == null || downloadUrl.isBlank()) {
                log.error("ONLYOFFICE callback URL is empty");
                return ResponseEntity.badRequest()
                        .body(Collections.singletonMap("error", 2));
            }

            try {
                log.info("Downloading edited document from: {}", downloadUrl);
                byte[] fileBytes = restTemplate.getForObject(downloadUrl, byte[].class);
                if (fileBytes == null) {
                    throw new RuntimeException("Failed to download file from ONLYOFFICE");
                }

                Document document = documentRepository.findById(documentId)
                        .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

                String storageKey = document.getStorageKey();
                boolean upsertSuccess = false;

                // Attempt atomic PUT/overwrite to Supabase Storage
                try {
                    String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + storageKey;
                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(serviceKey);
                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileBytes, headers);

                    log.info("Attempting atomic overwrite (PUT) in Supabase Storage for storage key: {}", storageKey);
                    ResponseEntity<String> response = restTemplate.exchange(
                            uploadUrl,
                            HttpMethod.PUT,
                            requestEntity,
                            String.class
                    );

                    if (response.getStatusCode().is2xxSuccessful()) {
                        upsertSuccess = true;
                        log.info("Atomic overwrite (PUT) successful for storage key: {}", storageKey);
                    } else {
                        log.warn("Atomic PUT returned non-success status code: {}, falling back to delete-then-upload", response.getStatusCode());
                    }
                } catch (Exception e) {
                    log.warn("Atomic PUT failed with error: {}, falling back to delete-then-upload", e.getMessage());
                }

                // Fallback to delete-then-upload
                if (!upsertSuccess) {
                    log.info("Executing delete-then-upload fallback for storage key: {}", storageKey);
                    try {
                        fileStorageService.deleteFile(storageKey);
                    } catch (Exception e) {
                        log.warn("Error deleting file during fallback (might not exist yet): {}", e.getMessage());
                    }
                    
                    File tempFile = File.createTempFile("onlyoffice-", ".tmp");
                    try {
                        Files.write(tempFile.toPath(), fileBytes);
                        fileStorageService.uploadFile(tempFile, storageKey);
                    } finally {
                        tempFile.delete();
                    }
                    log.info("Fallback upload successful for storage key: {}", storageKey);
                }

                // Update document size and timestamp, preserving all other metadata and database relationships
                document.setSizeBytes((long) fileBytes.length);
                document.setUpdatedAt(LocalDateTime.now());
                documentRepository.save(document);

                log.info("Document metadata successfully updated in DB for ID: {}", documentId);

            } catch (Exception e) {
                log.error("Failed to process ONLYOFFICE save callback for document ID: " + documentId, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Collections.singletonMap("error", 3));
            }
        }

        // Return error: 0 to tell ONLYOFFICE callback was successfully processed
        return ResponseEntity.ok(Collections.singletonMap("error", 0));
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
            throw new InvalidRequestException("Authorization token is required");
        }

        return jwtService.extractUserId(token);
    }

    private String generateCallbackToken(UUID documentId, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("documentId", documentId.toString());
        claims.put("userId", userId);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 12 * 60 * 60 * 1000)) // 12 hours
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes()))
                .compact();
    }

    private boolean validateCallbackToken(String token, UUID documentId) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String docIdStr = claims.get("documentId", String.class);
            return docIdStr != null && docIdStr.equals(documentId.toString());
        } catch (Exception e) {
            return false;
        }
    }

    private String getDocumentType(String ext) {
        if (ext == null) return "word";
        String extLower = ext.toLowerCase();
        if (java.util.List.of("docx", "doc", "txt", "rtf", "odt").contains(extLower)) return "word";
        if (java.util.List.of("xlsx", "xls", "csv", "ods").contains(extLower)) return "cell";
        if (java.util.List.of("pptx", "ppt", "odp").contains(extLower)) return "slide";
        return "word";
    }
}
