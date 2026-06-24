package com.docusphere.backend.documentVersion.service;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.audit.service.AuditService;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.documentVersion.entity.DocumentVersion;
import com.docusphere.backend.documentVersion.repository.DocumentVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Single entry point for document content updates with version snapshots.
 * ONLYOFFICE callback and multipart upload both flow through here — never create versions elsewhere.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentEditSaveService {

    private static final String DEFAULT_CHANGE_SUMMARY = "Document edited";

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final FileStorageService fileStorageService;
    private final DocumentVersionSummaryStore summaryStore;
    private final AuditService auditService;

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service.key}")
    private String serviceKey;

    @Value("${supabase.bucket.documents:documents}")
    private String bucketName;

    /**
     * ONLYOFFICE save callback — snapshot + overwrite in one transaction.
     */
    @Transactional
    public void processOnlyOfficeSave(
            UUID documentId,
            Long editedBy,
            byte[] fileBytes,
            String idempotencyKey
    ) {
        Document document = documentRepository.findActiveByIdForUpdate(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        LocalDateTime priorUpdatedAt = document.getUpdatedAt();
        String summary = resolveChangeSummary(documentId, editedBy, null);

        ensureInitialVersionSnapshot(document);

        persistDocumentContent(document, fileBytes);

        createVersionSnapshotIfNeeded(
                document,
                editedBy,
                summary,
                idempotencyKey,
                priorUpdatedAt,
                false,
                false,
                false,
                fileBytes,
                null
        );

        summaryStore.consume(documentId, editedBy);

        log.info("ONLYOFFICE save completed for document {} ({} bytes)", documentId, fileBytes.length);
    }

    private void persistDocumentContent(Document document, byte[] fileBytes) {
        overwriteStorageContent(document.getStorageKey(), fileBytes);
        document.setSizeBytes((long) fileBytes.length);
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);
    }

    /**
     * Multipart file upload path — snapshot + overwrite in one transaction.
     * Not used when editing via ONLYOFFICE.
     */
    @Transactional
    public Document processMultipartSave(
            UUID documentId,
            MultipartFile file,
            Long editedBy,
            String changeSummary,
            String idempotencyKey
    ) throws IOException {
        Document document = documentRepository.findActiveByIdForUpdate(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        LocalDateTime priorUpdatedAt = document.getUpdatedAt();
        String summary = resolveChangeSummary(documentId, editedBy, changeSummary);

        File tempFile = File.createTempFile("upload-", file.getOriginalFilename());
        try (var fos = Files.newOutputStream(tempFile.toPath())) {
            fos.write(file.getBytes());
        }

        ensureInitialVersionSnapshot(document);

        try {
            fileStorageService.deleteFile(document.getStorageKey());
        } catch (Exception ignored) {
            // Target may not exist yet
        }

        String publicUrl = fileStorageService.uploadFile(tempFile, document.getStorageKey());

        document.setFileUrl(publicUrl);
        if (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()) {
            document.setName(file.getOriginalFilename());
        }
        document.setSizeBytes(tempFile.length());
        document.setUpdatedAt(LocalDateTime.now());

        Document saved = documentRepository.save(document);

        createVersionSnapshotIfNeeded(
                saved,
                editedBy,
                summary,
                idempotencyKey,
                priorUpdatedAt,
                false,
                false,
                false,
                file.getBytes(),
                null
        );

        tempFile.delete();
        summaryStore.consume(documentId, editedBy);
        return saved;
    }

    /**
     * Restore backup snapshot — bypasses idempotency guard.
     */
    @Transactional
    public DocumentVersion createRestoreBackupSnapshot(Document document, Long editedBy, int restoredFromVersion, String sourceStorageKey) {
        return createVersionSnapshotIfNeeded(
                document,
                editedBy,
                "Backup before restore to version " + restoredFromVersion,
                null,
                document.getUpdatedAt(),
                false,
                false,
                true,
                null,
                sourceStorageKey
        ).orElseThrow(() -> new DocumentNotFoundException("Failed to create restore backup snapshot"));
    }

    private Optional<DocumentVersion> createVersionSnapshotIfNeeded(
            Document document,
            Long editedBy,
            String changeSummary,
            String idempotencyKey,
            LocalDateTime priorUpdatedAt,
            boolean markCurrent,
            boolean markRestored,
            boolean forceCreate,
            byte[] fileBytesOverride,
            String sourceStorageKeyOverride
    ) {
        if (!forceCreate && shouldSkipSnapshot(document.getId(), editedBy, idempotencyKey, priorUpdatedAt)) {
            log.info("Skipping duplicate version snapshot for document {}", document.getId());
            return documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(document.getId());
        }

        log.info("CREATING VERSION for documentId={}", document.getId());

        try {
            return Optional.of(createVersionSnapshot(
                    document,
                    editedBy,
                    changeSummary,
                    markCurrent,
                    markRestored,
                    LocalDateTime.now(),
                    fileBytesOverride,
                    sourceStorageKeyOverride
            ));
        } catch (Exception e) {
            log.error("VERSION CREATION FAILED for documentId={}", document.getId(), e);
            return Optional.empty();
        }
    }

    private void ensureInitialVersionSnapshot(Document document) {
        int maxVersion = documentVersionRepository.findMaxVersionNumberByDocumentId(document.getId()).orElse(0);
        if (maxVersion == 0) {
            log.info("Creating initial Version 1 snapshot for legacy document {}", document.getId());
            LocalDateTime originalDate = document.getCreatedAt() != null ? document.getCreatedAt() : LocalDateTime.now();
            createVersionSnapshot(
                    document,
                    document.getOwnerId(),
                    "Original upload",
                    false,
                    false,
                    originalDate,
                    null,
                    null
            );
        }
    }

    private DocumentVersion createVersionSnapshot(
            Document document,
            Long editedBy,
            String changeSummary,
            boolean markCurrent,
            boolean markRestored,
            LocalDateTime editedAt,
            byte[] fileBytesOverride,
            String sourceStorageKeyOverride
    ) {
        clearCurrentFlags(document.getId());

        int nextVersionNumber = resolveNextVersionNumber(document.getId());
        String versionStorageKey = buildVersionStorageKey(document.getId(), nextVersionNumber, document.getName());

        if (fileBytesOverride != null) {
            overwriteStorageContent(versionStorageKey, fileBytesOverride);
        } else {
            String sourceKey = sourceStorageKeyOverride != null ? sourceStorageKeyOverride : document.getStorageKey();
            fileStorageService.copyFile(sourceKey, versionStorageKey);
        }
        
        String fileUrl = fileStorageService.getPublicUrl(versionStorageKey);
        LocalDateTime now = LocalDateTime.now();

        DocumentVersion version = DocumentVersion.builder()
                .documentId(document.getId())
                .versionNumber(nextVersionNumber)
                .storageKey(versionStorageKey)
                .fileUrl(fileUrl)
                .sizeBytes(document.getSizeBytes() != null ? document.getSizeBytes() : 0L)
                .editedBy(editedBy)
                .editedAt(editedAt != null ? editedAt : LocalDateTime.now())
                .changeSummary(normalizeChangeSummary(changeSummary))
                .currentVersionSnapshot(markCurrent)
                .restored(markRestored)
                .build();

        DocumentVersion saved = documentVersionRepository.save(version);
        log.info(
                "insert into document_versions (id={}, document_id={}, version_number={}, storage_key={}, size_bytes={}, edited_by={})",
                saved.getId(),
                saved.getDocumentId(),
                saved.getVersionNumber(),
                saved.getStorageKey(),
                saved.getSizeBytes(),
                saved.getEditedBy()
        );

        recordAudit("VERSION_CREATED", document.getId(), saved.getId(), editedBy, Map.of(
                "versionNumber", saved.getVersionNumber(),
                "changeSummary", saved.getChangeSummary()
        ));

        return saved;
    }

    private boolean shouldSkipSnapshot(
            UUID documentId,
            Long editedBy,
            String idempotencyKey,
            LocalDateTime priorUpdatedAt
    ) {
        if (priorUpdatedAt == null) return false;

        return documentVersionRepository
                .findFirstByDocumentIdOrderByVersionNumberDesc(documentId)
                .filter(v -> v.getVersionNumber() > 1)
                .filter(v -> editedBy.equals(v.getEditedBy()))
                .filter(v -> v.getCreatedAt() != null)
                .filter(v -> v.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(30)))
                .isPresent();
    }

    private void overwriteStorageContent(String storageKey, byte[] fileBytes) {
        boolean upsertSuccess = false;
        try {
            String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + storageKey;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(serviceKey);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set("x-upsert", "true");
            HttpEntity<byte[]> requestEntity = new HttpEntity<>(fileBytes, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    uploadUrl,
                    HttpMethod.PUT,
                    requestEntity,
                    String.class
            );
            upsertSuccess = response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Atomic PUT failed for {}, falling back to delete-then-upload: {}", storageKey, e.getMessage());
        }

        if (!upsertSuccess) {
            try {
                fileStorageService.deleteFile(storageKey);
            } catch (Exception e) {
                log.warn("Delete before upload failed for {}: {}", storageKey, e.getMessage());
            }
            try {
                File tempFile = File.createTempFile("onlyoffice-", ".tmp");
                try {
                    Files.write(tempFile.toPath(), fileBytes);
                    fileStorageService.uploadFile(tempFile, storageKey);
                } finally {
                    tempFile.delete();
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload document content", e);
            }
        }
    }

    private void clearCurrentFlags(UUID documentId) {
        documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId).stream()
                .filter(DocumentVersion::isCurrentVersionSnapshot)
                .forEach(version -> {
                    version.setCurrentVersionSnapshot(false);
                    documentVersionRepository.save(version);
                });
    }

    private int resolveNextVersionNumber(UUID documentId) {
        return documentVersionRepository.findMaxVersionNumberByDocumentId(documentId)
                .map(max -> max + 1)
                .orElse(1);
    }

    private String buildVersionStorageKey(UUID documentId, int versionNumber, String fileName) {
        String safeName = fileName != null ? fileName.replaceAll("[^a-zA-Z0-9._-]", "_") : "document";
        return "Versions/" + documentId + "/v" + versionNumber + "_" + safeName;
    }

    private String normalizeChangeSummary(String changeSummary) {
        if (changeSummary == null || changeSummary.isBlank()) {
            return DEFAULT_CHANGE_SUMMARY;
        }
        return changeSummary.trim();
    }

    private String resolveChangeSummary(UUID documentId, Long editedBy, String changeSummary) {
        String pending = summaryStore.peek(documentId, editedBy);
        if (pending != null && !pending.isBlank()) {
            return normalizeChangeSummary(pending);
        }
        return normalizeChangeSummary(changeSummary);
    }

    private void recordAudit(String action, UUID documentId, UUID versionId, Long userId, Map<String, Object> extra) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("userId", userId);
        metadata.put("documentId", documentId.toString());
        metadata.put("versionId", versionId.toString());
        metadata.put("timestamp", LocalDateTime.now().toString());
        metadata.putAll(extra);
        auditService.record(action, metadata);
    }
}
