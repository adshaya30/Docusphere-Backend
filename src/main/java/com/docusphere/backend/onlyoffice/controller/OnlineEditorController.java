package com.docusphere.backend.onlyoffice.controller;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import com.docusphere.backend.onlyoffice.service.OnlyOfficeConfigBuilderService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/online-editor")
@RequiredArgsConstructor
public class OnlineEditorController {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final JwtService jwtService;
    private final OnlyOfficeConfigBuilderService configBuilderService;

    @Value("${jwt.secret}")
    private String secretKey;

    @PostMapping("/create")
    public ResponseEntity<CreateDocumentResponse> createDocument(
            @RequestBody CreateDocumentRequest createRequest,
            HttpServletRequest request
    ) {
        Long requesterId = extractRequesterId(request);
        User user = userRepository.findById(requesterId)
                .orElseThrow(() -> new InvalidRequestException("User not found"));

        String name = createRequest.getName();
        String type = createRequest.getType(); // word, spreadsheet/cell, presentation/slide

        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("Document name is required");
        }
        if (type == null || type.isBlank()) {
            throw new InvalidRequestException("Document type is required");
        }

        String extension = "docx";
        if ("spreadsheet".equalsIgnoreCase(type) || "cell".equalsIgnoreCase(type)) {
            extension = "xlsx";
        } else if ("presentation".equalsIgnoreCase(type) || "slide".equalsIgnoreCase(type)) {
            extension = "pptx";
        }

        String fullName = name.trim();
        if (!fullName.toLowerCase().endsWith("." + extension)) {
            fullName += "." + extension;
        }

        String fileId = UUID.randomUUID().toString();
        // sanitize name to match existing pattern
        String sanitizedName = fullName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String storageKey = fileId + "_" + sanitizedName;

        byte[] fileBytes;
        try {
            String templatePath = "templates/empty." + extension;
            ClassPathResource resource = new ClassPathResource(templatePath);
            try (var is = resource.getInputStream()) {
                fileBytes = StreamUtils.copyToByteArray(is);
            }
        } catch (Exception e) {
            log.error("Failed to load empty template for " + extension, e);
            throw new RuntimeException("Failed to load document template", e);
        }

        String fileUrl;
        try {
            File tempFile = File.createTempFile("online-editor-", "." + extension);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(fileBytes);
            }
            fileUrl = fileStorageService.uploadFile(tempFile, storageKey);
            tempFile.delete();
        } catch (Exception e) {
            log.error("Failed to upload created document to storage", e);
            throw new RuntimeException("Failed to save document to storage", e);
        }

        Document document = Document.builder()
                .fileId(fileId)
                .name(fullName)
                .type(extension)
                .sizeBytes((long) fileBytes.length)
                .ownerId(requesterId)
                .storageKey(storageKey)
                .fileUrl(fileUrl)
                .status(Document.UploadStatus.COMPLETED)
                .secured(false)
                .build();

        Document saved = documentRepository.save(document);
        log.info("Successfully created empty document in DB and Storage: ID={}, name={}", saved.getId(), saved.getName());

        // Generate OnlyOffice configuration for this newly created file
        boolean canEdit = true;
        boolean canView = true;
        boolean canDownload = true;

        long lastModified = saved.getUpdatedAt() != null
                ? java.sql.Timestamp.valueOf(saved.getUpdatedAt()).getTime()
                : (saved.getCreatedAt() != null ? java.sql.Timestamp.valueOf(saved.getCreatedAt()).getTime() : System.currentTimeMillis());

        String docKey = saved.getId().toString() + "_" + lastModified;
        docKey = docKey.replaceAll("[^a-zA-Z0-9_-]", "");
        if (docKey.length() > 20) {
            int hash = docKey.hashCode();
            docKey = "ds_" + saved.getId().toString().substring(0, 6) + "_" + Math.abs(hash);
        }

        String callbackToken = generateCallbackToken(saved.getId(), requesterId);

        OnlyOfficeConfig editorConfig = configBuilderService.buildConfig(
                saved,
                user,
                canEdit,
                canView,
                canDownload,
                docKey,
                callbackToken
        );

        return ResponseEntity.ok(new CreateDocumentResponse(saved.getId(), editorConfig));
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

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateDocumentRequest {
        private String name;
        private String type;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateDocumentResponse {
        private UUID documentId;
        private OnlyOfficeConfig editorConfig;
    }
}
