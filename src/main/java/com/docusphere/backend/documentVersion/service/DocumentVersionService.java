package com.docusphere.backend.documentVersion.service;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.audit.service.AuditService;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.documentProtection.service.DocumentPasswordProtectionService;
import com.docusphere.backend.documentShare.entity.DocumentShare;
import com.docusphere.backend.documentShare.service.DocumentSharingService;
import com.docusphere.backend.documentVersion.dto.DocumentVersionListResponse;
import com.docusphere.backend.documentVersion.dto.DocumentVersionResponse;
import com.docusphere.backend.documentVersion.dto.RestoreVersionResponse;
import com.docusphere.backend.documentVersion.dto.SaveChangeSummaryResponse;
import com.docusphere.backend.documentVersion.entity.DocumentVersion;
import com.docusphere.backend.documentVersion.repository.DocumentVersionRepository;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import com.docusphere.backend.onlyoffice.service.OnlyOfficeConfigBuilderService;
import com.docusphere.backend.onlyoffice.service.OnlyOfficeDownloadTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentVersionService {

    private static final String DEFAULT_CHANGE_SUMMARY = "Document edited";
    static final String SHARE_INVITATION_ATTRIBUTION_PREFIX = "Edited via shared invitation: ";

    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final DocumentVersionPermissionService permissionService;
    private final DocumentPasswordProtectionService documentPasswordProtectionService;
    private final DocumentSharingService documentSharingService;
    private final UserRepository userRepository;
    private final OnlyOfficeConfigBuilderService onlyOfficeConfigBuilderService;
    private final OnlyOfficeDownloadTokenService onlyOfficeDownloadTokenService;
    private final DocumentVersionSummaryStore summaryStore;
    private final DocumentEditSaveService editSaveService;
    private final AuditService auditService;

    /**
     * Stores pending change summary for the next ONLYOFFICE save.
     * Does NOT create version rows — versions are created only via ONLYOFFICE callback or multipart upload.
     */
    @Transactional
    public SaveChangeSummaryResponse saveChangeSummary(Long requesterId, UUID documentId, String changeSummary) {
        Document document = requireActiveDocument(documentId);
        ensureCanEdit(document, requesterId);

        String normalized = normalizeChangeSummary(changeSummary);
        if (!DEFAULT_CHANGE_SUMMARY.equals(normalized)) {
            summaryStore.store(documentId, requesterId, normalized);
            attachSummaryToLatestVersion(documentId, normalized);
        }

        return SaveChangeSummaryResponse.builder()
                .documentId(documentId)
                .changeSummary(normalized)
                .build();
    }

    @Transactional
    public SaveChangeSummaryResponse saveChangeSummaryForShare(
            String shareToken,
            UUID documentId,
            String changeSummary
    ) {
        Document document = requireActiveDocument(documentId);
        documentSharingService.requireEditPermission(documentId, shareToken);

        String normalized = normalizeChangeSummary(changeSummary);
        if (!DEFAULT_CHANGE_SUMMARY.equals(normalized)) {
            summaryStore.store(documentId, document.getOwnerId(), normalized);
            attachSummaryToLatestVersion(documentId, normalized);
        }

        return SaveChangeSummaryResponse.builder()
                .documentId(documentId)
                .changeSummary(normalized)
                .build();
    }

    void attachSummaryToLatestVersion(UUID documentId, String normalized) {
        if (DEFAULT_CHANGE_SUMMARY.equals(normalized)) {
            return;
        }
        documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(documentId)
                .filter(version -> version.getCreatedAt() != null
                        && version.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(120)))
                .ifPresent(version -> {
                    version.setChangeSummary(normalized);
                    documentVersionRepository.save(version);
                    log.info("Updated change summary on version {} for document {}", version.getVersionNumber(), documentId);
                });
    }

    @Transactional(readOnly = true)
    public DocumentVersionListResponse listVersions(Long requesterId, UUID documentId) {
        Document document = requireActiveDocument(documentId);
        ensureCanView(document, requesterId);
        return buildVersionListResponse(document);
    }

    @Transactional(readOnly = true)
    public DocumentVersionListResponse listVersionsForShare(String shareToken) {
        var shared = documentSharingService.openSharedDocument(shareToken);
        Document document = requireActiveDocument(shared.getDocumentId());
        return buildVersionListResponse(document);
    }

    @Transactional(readOnly = true)
    public DocumentVersionListResponse listVersionsByShareToken(String shareToken, UUID documentId) {
        documentSharingService.requireValidShareForDocument(documentId, shareToken);
        Document document = requireActiveDocument(documentId);
        return buildVersionListResponse(document);
    }

    private DocumentVersionListResponse buildVersionListResponse(Document document) {
        UUID documentId = document.getId();
        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
        int maxStoredVersion = versions.stream()
                .mapToInt(DocumentVersion::getVersionNumber)
                .max()
                .orElse(0);
        int currentVersionNumber = maxStoredVersion == 0 ? 1 : maxStoredVersion;

        List<DocumentVersionResponse> items = versions.stream()
                .map(version -> toResponse(document, version, maxStoredVersion))
                .collect(Collectors.toList());

        return DocumentVersionListResponse.builder()
                .documentId(documentId)
                .currentVersionNumber(currentVersionNumber)
                .isProtected(document.isPasswordProtected())
                .versions(items)
                .build();
    }

    @Transactional(readOnly = true)
    public DocumentVersionResponse getVersion(Long requesterId, UUID documentId, UUID versionId) {
        Document document = requireActiveDocument(documentId);
        ensureCanView(document, requesterId);

        DocumentVersion version = requireVersion(documentId, versionId);
        int maxStoredVersion = documentVersionRepository.findMaxVersionNumberByDocumentId(documentId).orElse(0);

        return toResponse(document, version, maxStoredVersion);
    }

    @Transactional(readOnly = true)
    public OnlyOfficeConfig previewVersion(
            Long requesterId,
            UUID documentId,
            UUID versionId,
            String password,
            String shareToken
    ) {
        Document document = requireActiveDocument(documentId);
        ensureCanView(document, requesterId, shareToken);
        ensurePasswordVerified(document, password, requesterId, shareToken);

        DocumentVersion version = requireVersion(documentId, versionId);
        User user = resolvePreviewUser(requesterId, shareToken, documentId);

        String docKey = buildPreviewDocKey(version);
        String versionTitle = "v" + version.getVersionNumber() + " - " + document.getName();
        String downloadToken = onlyOfficeDownloadTokenService.createVersionDownloadToken(
                documentId,
                versionId,
                requesterId,
                shareToken
        );

        OnlyOfficeConfig config = onlyOfficeConfigBuilderService.buildVersionPreviewConfig(
                document,
                version.getId(),
                versionTitle,
                docKey,
                user,
                downloadToken,
                shareToken
        );

        recordAudit("VERSION_PREVIEWED", documentId, versionId, requesterId, Map.of(
                "versionNumber", version.getVersionNumber()
        ));

        return config;
    }

    @Transactional(readOnly = true)
    public Resource downloadVersion(
            Long requesterId,
            UUID documentId,
            UUID versionId,
            String password,
            String shareToken
    ) {
        Document document = requireActiveDocument(documentId);
        ensureCanView(document, requesterId, shareToken);
        ensurePasswordVerified(document, password, requesterId, shareToken);

        DocumentVersion version = requireVersion(documentId, versionId);
        byte[] fileBytes = fileStorageService.loadFile(version.getStorageKey());

        recordAudit("VERSION_DOWNLOADED", documentId, versionId, requesterId, Map.of(
                "versionNumber", version.getVersionNumber()
        ));

        return new ByteArrayResource(fileBytes);
    }

    @Transactional(readOnly = true)
    public Resource downloadVersionForSystem(UUID documentId, UUID versionId, String dlToken) {
        Document document = requireActiveDocument(documentId);
        DocumentVersion version = requireVersion(documentId, versionId);

        OnlyOfficeDownloadContext context = resolveOnlyOfficeVersionDownloadContext(documentId, versionId, dlToken);
        ensurePasswordVerified(document, null, context.userId(), context.shareToken());

        byte[] fileBytes = fileStorageService.loadFile(version.getStorageKey());
        return new ByteArrayResource(fileBytes);
    }

    private OnlyOfficeDownloadContext resolveOnlyOfficeVersionDownloadContext(UUID documentId, UUID versionId, String dlToken) {
        if (dlToken == null || dlToken.isBlank()) {
            throw new UnauthorizedAccessException("Download token is required");
        }

        io.jsonwebtoken.Claims claims = onlyOfficeDownloadTokenService.parseToken(dlToken.trim());
        String claimDocumentId = claims.get("documentId", String.class);
        String claimVersionId = claims.get("versionId", String.class);
        if (claimDocumentId == null || !claimDocumentId.equals(documentId.toString())
                || claimVersionId == null || !claimVersionId.equals(versionId.toString())) {
            throw new UnauthorizedAccessException("Download token does not match version");
        }

        Long userId = claims.get("userId", Number.class) != null
                ? claims.get("userId", Number.class).longValue()
                : null;
        String shareToken = claims.get("shareToken", String.class);
        Document document = requireActiveDocument(documentId);

        if (shareToken != null && !shareToken.isBlank()) {
            if (!permissionService.canView(document, userId, shareToken)) {
                throw new UnauthorizedAccessException("You do not have access to view version history for this document");
            }
        } else if (userId != null) {
            ensureCanView(document, userId, null);
        } else {
            throw new UnauthorizedAccessException("Download token is invalid");
        }

        return new OnlyOfficeDownloadContext(userId, shareToken);
    }

    private record OnlyOfficeDownloadContext(Long userId, String shareToken) {
    }

    @Transactional(readOnly = true)
    public String resolveDownloadFilename(Long requesterId, UUID documentId, UUID versionId) {
        Document document = requireActiveDocument(documentId);
        if (requesterId != null) {
            ensureCanView(document, requesterId, null);
        }

        DocumentVersion version = requireVersion(documentId, versionId);
        return "v" + version.getVersionNumber() + "_" + document.getName();
    }

    @Transactional
    public RestoreVersionResponse restoreVersion(Long requesterId, UUID documentId, UUID versionId) {
        Document document = requireActiveDocument(documentId);
        ensureCanRestore(document, requesterId);

        DocumentVersion selectedVersion = requireVersion(documentId, versionId);

        // Replace document content with the restored version
        replaceDocumentContent(document.getStorageKey(), selectedVersion.getStorageKey());
        document.setSizeBytes(selectedVersion.getSizeBytes());
        document.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(document);

        // Create a NEW version for the restored state
        DocumentVersion newCurrentVersion = editSaveService.createRestoreBackupSnapshot(
                document,
                requesterId,
                selectedVersion.getVersionNumber(),
                selectedVersion.getStorageKey()
        );
        newCurrentVersion.setChangeSummary("Restored from version " + selectedVersion.getVersionNumber());
        newCurrentVersion.setCurrentVersionSnapshot(true);
        newCurrentVersion.setRestored(true);
        documentVersionRepository.save(newCurrentVersion);

        // Ensure the old version is not marked as current anymore
        selectedVersion.setCurrentVersionSnapshot(false);
        documentVersionRepository.save(selectedVersion);

        recordAudit("VERSION_RESTORED", documentId, versionId, requesterId, Map.of(
                "restoredFromVersionNumber", selectedVersion.getVersionNumber(),
                "newVersionNumber", newCurrentVersion.getVersionNumber()
        ));

        return RestoreVersionResponse.builder()
                .documentId(documentId)
                .restoredFromVersionId(selectedVersion.getId())
                .restoredFromVersionNumber(selectedVersion.getVersionNumber())
                .restoredAt(LocalDateTime.now())
                .build();
    }

    private Document requireActiveDocument(UUID documentId) {
        return documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));
    }

    private DocumentVersion requireVersion(UUID documentId, UUID versionId) {
        return documentVersionRepository.findByIdAndDocumentId(versionId, documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Version not found"));
    }

    private void ensureCanView(Document document, Long requesterId, String shareToken) {
        if (!permissionService.canView(document, requesterId, shareToken)) {
            throw new UnauthorizedAccessException("You do not have access to view version history for this document");
        }
    }

    private User resolvePreviewUser(Long requesterId, String shareToken, UUID documentId) {
        if (requesterId != null) {
            return userRepository.findById(requesterId)
                    .orElseThrow(() -> new DocumentNotFoundException("User not found"));
        }
        if (shareToken != null && !shareToken.isBlank()) {
            DocumentShare share = documentSharingService.requireValidShareForDocument(documentId, shareToken);
            User guest = new User();
            guest.setId(0L);
            guest.setFullName(share.getEmail() != null ? share.getEmail() : "Guest");
            return guest;
        }
        throw new UnauthorizedAccessException("You do not have access to view this document");
    }

    private void ensureCanView(Document document, Long requesterId) {
        ensureCanView(document, requesterId, null);
    }

    private void ensureCanRestore(Document document, Long requesterId) {
        if (!permissionService.canRestore(document, requesterId)) {
            throw new UnauthorizedAccessException("You do not have permission to restore versions of this document");
        }
    }

    private void ensureCanEdit(Document document, Long requesterId) {
        if (!permissionService.canEdit(document, requesterId)) {
            throw new UnauthorizedAccessException("You do not have permission to edit this document");
        }
    }

    private void ensurePasswordVerified(
            Document document,
            String password,
            Long requesterId,
            String shareToken
    ) {
        documentPasswordProtectionService.requirePasswordForContentAccess(
                document,
                password,
                requesterId,
                shareToken
        );
    }

    private String buildPreviewDocKey(DocumentVersion version) {
        String docKey = version.getId().toString().replace("-", "");
        if (version.getEditedAt() != null) {
            docKey = docKey + "_" + java.sql.Timestamp.valueOf(version.getEditedAt()).getTime();
        }
        docKey = docKey.replaceAll("[^a-zA-Z0-9_-]", "");
        if (docKey.length() > 20) {
            docKey = "vv_" + version.getId().toString().substring(0, 6) + "_" + Math.abs(docKey.hashCode()) + "_v2";
        }
        return docKey;
    }

    private void replaceDocumentContent(String targetPath, String sourcePath) {
        try {
            fileStorageService.deleteFile(targetPath);
        } catch (Exception e) {
            log.warn("Could not delete target file before replace (may not exist): {}", targetPath);
        }
        fileStorageService.copyFile(sourcePath, targetPath);
    }

    private String normalizeChangeSummary(String changeSummary) {
        if (changeSummary == null || changeSummary.isBlank()) {
            return DEFAULT_CHANGE_SUMMARY;
        }
        return changeSummary.trim();
    }

    private DocumentVersionResponse toResponse(Document document, DocumentVersion version, int maxStoredVersion) {
        boolean viaShareInvitation = hasShareInvitationEmail(version);
        return DocumentVersionResponse.builder()
                .versionId(version.getId())
                .versionNumber(version.getVersionNumber())
                .editedById(version.getEditedBy())
                .editedByName(resolveEditorName(version))
                .editedByEmail(viaShareInvitation ? version.getEditedByEmail().trim() : null)
                .editedViaShareInvitation(viaShareInvitation)
                .editedByRole(permissionService.resolveEditorRole(document, version.getEditedBy()))
                .editedAt(version.getEditedAt())
                .createdAt(version.getCreatedAt())
                .changeSummary(version.getChangeSummary())
                .isCurrent(version.isCurrentVersionSnapshot())
                .isLatest(version.getVersionNumber() == maxStoredVersion)
                .isRestored(version.isRestored())
                .isProtected(document.isPasswordProtected())
                .fileSize(version.getSizeBytes())
                .build();
    }

    private boolean hasShareInvitationEmail(DocumentVersion version) {
        return version.getEditedByEmail() != null && !version.getEditedByEmail().isBlank();
    }

    private String resolveEditorName(DocumentVersion version) {
        if (hasShareInvitationEmail(version)) {
            return SHARE_INVITATION_ATTRIBUTION_PREFIX + version.getEditedByEmail().trim();
        }
        return resolveEditorName(version.getEditedBy());
    }

    private String resolveEditorName(Long userId) {
        return userRepository.findById(userId)
                .map(User::getFullName)
                .orElse("Unknown User");
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