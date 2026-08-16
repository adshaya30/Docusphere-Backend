package com.docusphere.backend.editor.service;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.Common.exception.FileUploadException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.editor.dto.CreateEditorDocumentRequest;
import com.docusphere.backend.editor.dto.SaveEditorDocumentRequest;
import com.docusphere.backend.editor.entity.EditorDocument;
import com.docusphere.backend.editor.repository.EditorDocumentRepository;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeCallback;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class EditorServiceImpl implements EditorService {

    private final EditorDocumentRepository repository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${app.onlyoffice.callback-url:http://localhost:8080/api/onlyoffice/callback}")
    private String onlyofficeCallbackUrl;

    @Value("${app.onlyoffice.jwt.secret:V8pX9iu5gDWzQrHP5Od62XOOiuOnlrtF}")
    private String onlyofficeJwtSecret;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service.key}")
    private String serviceKey;

    @Value("${supabase.bucket.documents:documents}")
    private String bucketName;

    public EditorServiceImpl(
            EditorDocumentRepository repository,
            UserRepository userRepository,
            FileStorageService fileStorageService
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public OnlyOfficeConfig createDocument(Long ownerId, CreateEditorDocumentRequest request) {
        log.info("Creating new empty editor document: name='{}', type='{}' for ownerId={}",
                request.getDocumentName(), request.getDocumentType(), ownerId);

        String docType = request.getDocumentType().toLowerCase().trim();
        String fileExtension;
        if (docType.equals("word") || docType.equals("docx")) {
            fileExtension = "docx";
        } else if (docType.equals("excel") || docType.equals("xlsx")) {
            fileExtension = "xlsx";
        } else if (docType.equals("presentation") || docType.equals("pptx")) {
            fileExtension = "pptx";
        } else {
            throw new InvalidRequestException("Unsupported document type: " + request.getDocumentType());
        }

        UUID documentId = UUID.randomUUID();
        String storagePath = "editor-documents/" + ownerId + "/" + documentId + "." + fileExtension;

        // Load empty template from classpath
        String templateResourcePath = "templates/empty." + fileExtension;
        byte[] fileBytes;
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(templateResourcePath)) {
            if (is == null) {
                throw new FileUploadException("Template resource not found: " + templateResourcePath);
            }
            fileBytes = is.readAllBytes();
        } catch (Exception e) {
            log.error("Failed to load blank document template", e);
            throw new FileUploadException("Failed to read document template: " + e.getMessage());
        }

        // Upload empty template to Supabase Storage
        File tempFile = null;
        try {
            tempFile = File.createTempFile("editor-empty-", "." + fileExtension);
            Files.write(tempFile.toPath(), fileBytes);
            fileStorageService.uploadFile(tempFile, storagePath);
        } catch (Exception e) {
            log.error("Failed to upload blank template to storage", e);
            throw new FileUploadException("Failed to write empty document to storage: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }

        // Save metadata
        EditorDocument editorDoc = EditorDocument.builder()
                .id(documentId)
                .ownerId(ownerId)
                .name(request.getDocumentName())
                .type(fileExtension)
                .storagePath(storagePath)
                .status("CREATED")
                .build();
        repository.save(editorDoc);

        log.info("Editor document metadata saved. ID={}", documentId);

        return buildOnlyOfficeConfig(editorDoc, ownerId, true);
    }

    @Override
    public List<EditorDocument> listDocuments(Long ownerId) {
        log.info("Fetching editor documents list for ownerId={}", ownerId);
        return repository.findByOwnerId(ownerId);
    }

    @Override
    public OnlyOfficeConfig getDocumentConfig(Long ownerId, UUID documentId) {
        log.info("Opening editor config for documentId={} by ownerId={}", documentId, ownerId);
        EditorDocument document = repository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        if (!document.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedAccessException("You do not own this document");
        }

        return buildOnlyOfficeConfig(document, ownerId, true);
    }

    @Override
    public EditorDocument saveMetadata(Long ownerId, SaveEditorDocumentRequest request) {
        log.info("Saving metadata for documentId={} by ownerId={}", request.getId(), ownerId);
        EditorDocument document = repository.findById(request.getId())
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        if (!document.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedAccessException("You do not own this document");
        }

        document.setName(request.getName());
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            document.setStatus(request.getStatus());
        }
        document.setUpdatedAt(LocalDateTime.now());
        return repository.save(document);
    }

    @Override
    public void deleteDocument(Long ownerId, UUID documentId) {
        log.info("Deleting editor document documentId={} by ownerId={}", documentId, ownerId);
        EditorDocument document = repository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        if (!document.getOwnerId().equals(ownerId)) {
            throw new UnauthorizedAccessException("You do not own this document");
        }

        try {
            fileStorageService.deleteFile(document.getStoragePath());
        } catch (Exception e) {
            log.warn("Could not delete file from Supabase storage during deletion of document ID: {}", documentId, e);
        }

        repository.delete(document);
        log.info("Editor document ID={} successfully deleted", documentId);
    }

    @Override
    public void handleSaveCallback(UUID id, String token, OnlyOfficeCallback callback) {
        log.info("Received ONLYOFFICE callback for editor document ID: {} with status: {}", id, callback.getStatus());

        if (!validateCallbackToken(token, id)) {
            log.error("Invalid ONLYOFFICE callback token for editor document ID: {}", id);
            throw new UnauthorizedAccessException("Invalid callback token");
        }

        // Status 2 is ready for save, Status 6 is force save
        if (callback.getStatus() != null && (callback.getStatus() == 2 || callback.getStatus() == 6)) {
            String downloadUrl = callback.getUrl();
            if (downloadUrl == null || downloadUrl.isBlank()) {
                log.error("ONLYOFFICE callback URL is empty");
                throw new InvalidRequestException("Callback URL is missing");
            }

            try {
                log.info("Downloading updated document version from ONLYOFFICE: {}", downloadUrl);
                byte[] fileBytes = restTemplate.getForObject(downloadUrl, byte[].class);
                if (fileBytes == null) {
                    throw new RuntimeException("Failed to download file from ONLYOFFICE callback");
                }

                EditorDocument document = repository.findById(id)
                        .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

                String storageKey = document.getStoragePath();
                boolean upsertSuccess = false;

                // Attempt atomic PUT/overwrite to Supabase Storage
                try {
                    String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + storageKey;
                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(serviceKey);
                    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileBytes, headers);

                    log.info("Attempting atomic overwrite (PUT) in Supabase Storage for editor storage key: {}", storageKey);
                    ResponseEntity<String> response = restTemplate.exchange(
                            uploadUrl,
                            HttpMethod.PUT,
                            requestEntity,
                            String.class
                    );

                    if (response.getStatusCode().is2xxSuccessful()) {
                        upsertSuccess = true;
                        log.info("Atomic overwrite (PUT) successful for editor storage key: {}", storageKey);
                    }
                } catch (Exception e) {
                    log.warn("Atomic PUT failed for editor storage key: {}, falling back to delete-then-upload", e.getMessage());
                }

                // Fallback to delete-then-upload
                if (!upsertSuccess) {
                    log.info("Executing delete-then-upload fallback for editor storage key: {}", storageKey);
                    try {
                        fileStorageService.deleteFile(storageKey);
                    } catch (Exception e) {
                        log.warn("Error deleting file during fallback: {}", e.getMessage());
                    }
                    
                    File tempFile = File.createTempFile("editor-onlyoffice-", ".tmp");
                    try {
                        Files.write(tempFile.toPath(), fileBytes);
                        fileStorageService.uploadFile(tempFile, storageKey);
                    } finally {
                        tempFile.delete();
                    }
                    log.info("Fallback upload successful for editor storage key: {}", storageKey);
                }

                document.setUpdatedAt(LocalDateTime.now());
                document.setStatus("SAVED");
                repository.save(document);
                log.info("Editor document database metadata updated for ID: {}", id);

            } catch (Exception e) {
                log.error("Failed to process ONLYOFFICE save callback for editor document ID: {}", id, e);
                throw new RuntimeException("Save callback processing failed", e);
            }
        }
    }

    // ──────────────────────────── Private Helpers ────────────────────────────────

    private OnlyOfficeConfig buildOnlyOfficeConfig(EditorDocument document, Long userId, boolean canEdit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidRequestException("User not found"));

        String fileExtension = document.getType().toLowerCase();
        String baseCallbackUrl = onlyofficeCallbackUrl.replace("/api/onlyoffice/callback", "/api/editor/callback");
        String callbackToken = generateCallbackToken(document.getId(), userId);
        String callbackUrl = baseCallbackUrl + "?id=" + document.getId() + "&token=" + callbackToken;

        OnlyOfficeConfig.Permissions permissions = OnlyOfficeConfig.Permissions.builder()
                .edit(canEdit)
                .comment(true)
                .download(true)
                .print(true)
                .build();

        OnlyOfficeConfig.UserInfo userInfo = OnlyOfficeConfig.UserInfo.builder()
                .id(String.valueOf(user.getId()))
                .name(user.getFullName())
                .build();

        // Customization
        OnlyOfficeConfig.GoBack goBack = OnlyOfficeConfig.GoBack.builder()
                .url("") // Set redirect or leave blank
                .build();

        OnlyOfficeConfig.Customization customization = OnlyOfficeConfig.Customization.builder()
                .forcesave(true)
                .autosave(true)
                .goback(goBack)
                .build();

        OnlyOfficeConfig.EditorConfig editorConfig = OnlyOfficeConfig.EditorConfig.builder()
                .mode(canEdit ? "edit" : "view")
                .lang("en")
                .user(userInfo)
                .customization(customization)
                .callbackUrl(callbackUrl)
                .build();

        long lastModified = document.getUpdatedAt() != null 
                ? java.sql.Timestamp.valueOf(document.getUpdatedAt()).getTime() 
                : (document.getCreatedAt() != null ? java.sql.Timestamp.valueOf(document.getCreatedAt()).getTime() : System.currentTimeMillis());

        String fileUrl = fileStorageService.getPublicUrl(document.getStoragePath());
        if (fileUrl != null) {
            String separator = fileUrl.contains("?") ? "&" : "?";
            fileUrl = fileUrl + separator + "cb=" + lastModified;
        }

        String docKey = document.getId().toString() + "_" + lastModified;
        docKey = docKey.replaceAll("[^a-zA-Z0-9_-]", "");
        if (docKey.length() > 20) {
            int hash = docKey.hashCode();
            docKey = "ds_" + document.getId().toString().substring(0, 6) + "_" + Math.abs(hash);
        }

        OnlyOfficeConfig.DocumentInfo documentInfo = OnlyOfficeConfig.DocumentInfo.builder()
                .fileType(fileExtension)
                .key(docKey)
                .title(document.getName())
                .url(fileUrl)
                .permissions(permissions)
                .build();

        Map<String, Object> claims = new HashMap<>();
        claims.put("document", documentInfo);
        claims.put("editorConfig", editorConfig);
        claims.put("documentType", getDocumentType(fileExtension));

        String token;
        try {
            token = Jwts.builder()
                    .claims(claims)
                    .signWith(Keys.hmacShaKeyFor(onlyofficeJwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .compact();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate ONLYOFFICE security token for editor", e);
        }

        return OnlyOfficeConfig.builder()
                .documentType(getDocumentType(fileExtension))
                .document(documentInfo)
                .editorConfig(editorConfig)
                .width("100%")
                .height("100%")
                .token(token)
                .build();
    }

    private String generateCallbackToken(UUID documentId, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("documentId", documentId.toString());
        claims.put("userId", userId);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 12 * 60 * 60 * 1000)) // 12 hours
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();
    }

    private boolean validateCallbackToken(String token, UUID documentId) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
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
        if (List.of("docx", "doc", "txt", "rtf", "odt").contains(extLower)) return "word";
        if (List.of("xlsx", "xls", "csv", "ods").contains(extLower)) return "cell";
        if (List.of("pptx", "ppt", "odp").contains(extLower)) return "slide";
        return "word";
    }
}
