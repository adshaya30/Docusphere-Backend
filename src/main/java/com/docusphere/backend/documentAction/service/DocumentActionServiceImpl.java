package com.docusphere.backend.documentAction.service;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.Common.exception.FileUploadException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.audit.service.AuditService;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentShare.service.DocumentSharingService;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.documentProtection.service.DocumentPasswordProtectionService;
import com.docusphere.backend.documentAction.dto.DocumentActionResponse;
import com.docusphere.backend.documentAction.dto.TrashDocumentItemResponse;
import com.docusphere.backend.documentAction.dto.TrashDocumentsPageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.HashMap;

@Service
public class DocumentActionServiceImpl implements DocumentActionService {

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final TeamAccessValidator teamAccessValidator;
    private final AuditService auditService;
    private final DocumentSharingService documentSharingService;
    private final DocumentPasswordProtectionService documentPasswordProtectionService;
    private final String supabaseUrl;
    private final String bucketName;
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    public DocumentActionServiceImpl(
            DocumentRepository documentRepository,
            FileStorageService fileStorageService,
            TeamAccessValidator teamAccessValidator,
            AuditService auditService,
            DocumentSharingService documentSharingService,
            DocumentPasswordProtectionService documentPasswordProtectionService,
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.bucket.documents:documents}") String bucketName
    ) {
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.teamAccessValidator = teamAccessValidator;
        this.auditService = auditService;
        this.documentSharingService = documentSharingService;
        this.documentPasswordProtectionService = documentPasswordProtectionService;
        this.supabaseUrl = supabaseUrl;
        this.bucketName = bucketName;
    }

    public DocumentActionServiceImpl(
            DocumentRepository documentRepository,
            FileStorageService fileStorageService,
            TeamAccessValidator teamAccessValidator,
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.bucket.documents:documents}") String bucketName
    ) {
        this(documentRepository, fileStorageService, teamAccessValidator, null, null, null, supabaseUrl, bucketName);
    }

    @Override
    @Transactional
    public DocumentActionResponse rename(Long requesterId, UUID documentId, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new InvalidRequestException("newName is required");
        }

        Document document = requireActiveDocument(documentId);
        ensureOwner(document, requesterId);
        document.setName(newName.trim());
        return toResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public DocumentActionResponse move(Long requesterId, UUID documentId, UUID targetTeamId) {
        Document document = requireActiveDocument(documentId);
        ensureOwner(document, requesterId);

        if (targetTeamId != null && targetTeamId.equals(document.getTeamId())) {
            throw new InvalidRequestException("Document already in target space");
        }

        if (targetTeamId != null) {
            if (!isMemberOfTargetTeam(requesterId, targetTeamId)) {
                throw new UnauthorizedAccessException("User is not a member of the target team");
            }
        }

        // Block moving password-protected documents into Team Spaces.
        // Requirement: if document is secured && destination is a Team (targetTeamId != null) -> reject
        if (targetTeamId != null && document.isPasswordProtected()) {
            // Record audit entry if audit service available
            if (auditService != null) {
                try {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("documentId", documentId.toString());
                    meta.put("ownerId", document.getOwnerId());
                    meta.put("requesterId", requesterId);
                    meta.put("targetTeamId", targetTeamId.toString());
                    auditService.record("MOVE_BLOCKED_PROTECTED_FILE", meta);
                } catch (Exception ex) {
                    // never fail the request because audit failed
                }
            }

            throw new InvalidRequestException("Password protected documents cannot be moved to Team Spaces. Remove protection first or use Share.");
        }

        document.setTeamId(targetTeamId);
        return toResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public DocumentActionResponse duplicate(Long requesterId, UUID documentId) {
        Document original = requireActiveDocument(documentId);
        ensureAccessible(original, requesterId);

        String duplicatedName = generateDuplicateName(original.getName(), requesterId, original.getTeamId());
        String duplicatedFileId = UUID.randomUUID().toString();
        String duplicatedStorageKey = fileStorageService.copyFile(
                original.getStorageKey(),
                buildDuplicatedStorageKey(original.getStorageKey(), duplicatedFileId)
        );

        Document duplicate = Document.builder()
                .fileId(duplicatedFileId)
                .name(duplicatedName)
                .type(original.getType())
                .sizeBytes(original.getSizeBytes())
                .ownerId(requesterId)
                .teamId(original.getTeamId())
                .storageKey(duplicatedStorageKey)
                .fileUrl(generateNewUrl(duplicatedStorageKey))
                .status(original.getStatus())
                .secured(original.isSecured())
                .passwordHash(original.isSecured() ? original.getPasswordHash() : null)
                .deleted(false)
                .build();

        return toResponse(documentRepository.save(duplicate));
    }

    @Override
    @Transactional
    public DocumentActionResponse moveToTrash(Long requesterId, UUID documentId) {
        Document document = requireActiveDocument(documentId);
        ensureOwner(document, requesterId);

        document.setDeleted(true);
        document.setDeletedAt(LocalDateTime.now());
        return toResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public DocumentActionResponse restoreFromTrash(Long requesterId, UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        ensureOwner(document, requesterId);

        if (!document.isDeleted()) {
            throw new InvalidRequestException("Document is not in trash");
        }

        document.setDeleted(false);
        document.setDeletedAt(null);
        return toResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public void permanentlyDelete(Long requesterId, UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        ensureOwner(document, requesterId);

        if (!document.isDeleted()) {
            throw new InvalidRequestException("Only trashed documents can be permanently deleted");
        }

        fileStorageService.deleteFile(document.getStorageKey());
        documentRepository.delete(document);
    }

    @Override
    @Transactional(readOnly = true)
    public TrashDocumentsPageResponse getTrash(Long requesterId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deletedAt"));
        Page<Document> trashPage = documentRepository.findByOwnerIdAndDeletedTrue(requesterId, pageable);

        return TrashDocumentsPageResponse.builder()
                .items(trashPage.getContent().stream().map(this::toTrashItem).toList())
                .page(trashPage.getNumber())
                .size(trashPage.getSize())
                .totalElements(trashPage.getTotalElements())
                .totalPages(trashPage.getTotalPages())
                .first(trashPage.isFirst())
                .last(trashPage.isLast())
                .empty(trashPage.isEmpty())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Resource download(Long requesterId, UUID documentId, String password) {
        Document document = requireActiveDocument(documentId);
        ensureAccessible(document, requesterId);
        ensurePasswordVerified(document, password, requesterId, null);
        return new ByteArrayResource(loadDocumentBytes(document));
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadForSystem(UUID documentId) {
        Document document = requireActiveDocument(documentId);
        return new ByteArrayResource(loadDocumentBytes(document));
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveDownloadFilename(Long requesterId, UUID documentId, String password) {
        Document document = requireActiveDocument(documentId);
        ensureAccessible(document, requesterId);
        ensurePasswordVerified(document, password, requesterId, null);
        return document.getName();
    }

    @Override
    @Transactional(readOnly = true)
    public Resource downloadByShareToken(UUID documentId, String token, String password) {
        if (documentSharingService == null) {
            throw new FileUploadException("Document sharing service is not available");
        }
        Document document = documentSharingService.checkReadAccessByShareToken(documentId, token);
        ensurePasswordVerified(document, password, null, token);
        return new ByteArrayResource(loadDocumentBytes(document));
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveDownloadFilenameByShareToken(UUID documentId, String token, String password) {
        if (documentSharingService == null) {
            throw new FileUploadException("Document sharing service is not available");
        }
        Document document = documentSharingService.checkReadAccessByShareToken(documentId, token);
        ensurePasswordVerified(document, password, null, token);
        return document.getName();
    }

    private void ensurePasswordVerified(Document document, String password, Long requesterId, String shareToken) {
        if (documentPasswordProtectionService == null) {
            return;
        }
        documentPasswordProtectionService.requirePasswordForContentAccess(
                document,
                password,
                requesterId,
                shareToken
        );
    }

    private Document requireActiveDocument(UUID documentId) {
        return documentRepository.findByIdAndDeletedFalse(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));
    }

    private void ensureOwner(Document document, Long requesterId) {
        if (!document.getOwnerId().equals(requesterId)) {
            throw new UnauthorizedAccessException("Only the owner can perform this action");
        }
    }

    private void ensureAccessible(Document document, Long requesterId) {
        if (document.getOwnerId().equals(requesterId)) {
            return;
        }

        UUID teamId = document.getTeamId();
        if (teamId != null && isMemberOfTargetTeam(requesterId, teamId)) {
            return;
        }

        throw new UnauthorizedAccessException("You do not have access to this document");
    }

    private String generateDuplicateName(String originalName, Long ownerId, UUID teamId) {
        String baseName = originalName;
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < originalName.length() - 1) {
            baseName = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }

        int counter = 1;
        String candidate = baseName + " (" + counter + ")" + extension;
        while (nameExists(ownerId, teamId, candidate)) {
            counter++;
            candidate = baseName + " (" + counter + ")" + extension;
        }
        return candidate;
    }

    private boolean nameExists(Long ownerId, UUID teamId, String candidate) {
        if (teamId == null) {
            return documentRepository.existsByOwnerIdAndNameAndDeletedFalse(ownerId, candidate);
        }
        return documentRepository.existsByOwnerIdAndTeamIdAndNameAndDeletedFalse(ownerId, teamId, candidate);
    }

    private String buildDuplicatedStorageKey(String sourceKey, String newFileId) {
        int lastSlash = sourceKey.lastIndexOf('/');
        String fileNamePart = lastSlash >= 0 ? sourceKey.substring(lastSlash + 1) : sourceKey;
        int underscore = fileNamePart.indexOf('_');
        String suffix = underscore >= 0 ? fileNamePart.substring(underscore) : "_" + fileNamePart;
        String replacedFileName = newFileId + suffix;
        if (lastSlash >= 0) {
            return sourceKey.substring(0, lastSlash + 1) + replacedFileName;
        }
        return replacedFileName;
    }

    private boolean isMemberOfTargetTeam(Long userId, UUID teamId) {
        return teamAccessValidator.isMember(userId, teamId);
    }

    private String generateNewUrl(String storageKey) {
        return supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + storageKey;
    }

    private byte[] loadDocumentBytes(Document document) {
        List<String> candidateStorageKeys = resolveStorageKeyCandidates(document);
        Exception lastStorageException = null;

        for (String candidateStorageKey : candidateStorageKeys) {
            try {
                return fileStorageService.loadFile(candidateStorageKey);
            } catch (Exception ex) {
                lastStorageException = ex;
            }
        }

        try {
            for (String candidateStorageKey : candidateStorageKeys) {
                return loadFromPublicUrl(fileStorageService.getPublicUrl(candidateStorageKey));
            }
            return loadFromPublicUrl(document.getFileUrl());
        } catch (Exception publicUrlException) {
            throw new FileUploadException("Unable to download file from storage", lastStorageException != null
                    ? lastStorageException
                    : publicUrlException);
        }
    }

    private List<String> resolveStorageKeyCandidates(Document document) {
        Set<String> candidates = new LinkedHashSet<>();

        String storageKey = normalize(document.getStorageKey());
        if (storageKey != null) {
            candidates.add(storageKey);
        }

        String keyFromUrl = extractStorageKeyFromFileUrl(document.getFileUrl());
        if (keyFromUrl != null) {
            candidates.add(keyFromUrl);
        }

        String fileId = document.getFileId();
        String name = document.getName();
        if (fileId == null || fileId.isBlank() || name == null || name.isBlank()) {
            return new ArrayList<>(candidates);
        }

        String safeName = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        candidates.add("Documents/" + fileId + "_" + safeName);
        candidates.add(fileId + "_" + safeName);

        return new ArrayList<>(candidates);
    }

    private byte[] loadFromPublicUrl(String publicUrl) {
        String normalizedUrl = normalize(publicUrl);
        if (normalizedUrl == null) {
            throw new FileUploadException("Unable to download file from storage");
        }
        try {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(normalizedUrl, byte[].class);
            byte[] body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw new FileUploadException("Unable to download file from public URL");
            }
            return body;
        } catch (Exception publicUrlException) {
            throw new FileUploadException("Unable to download file from storage", publicUrlException);
        }
    }

    private String extractStorageKeyFromFileUrl(String fileUrl) {
        String normalized = normalize(fileUrl);
        if (normalized == null) {
            return null;
        }

        String marker = "/storage/v1/object/public/" + bucketName + "/";
        int markerIndex = normalized.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }

        String key = normalized.substring(markerIndex + marker.length());
        return normalize(key);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

   private DocumentActionResponse toResponse(Document doc) {
        return DocumentActionResponse.builder()
                .documentId(doc.getId())
                .name(doc.getName())
                .ownerId(doc.getOwnerId())
                .teamId(doc.getTeamId())
                .storageKey(doc.getStorageKey())
                .deleted(doc.isDeleted())
                .deletedAt(doc.getDeletedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private TrashDocumentItemResponse toTrashItem(Document doc) {
        return TrashDocumentItemResponse.builder()
                .id(doc.getId())
                .fileId(doc.getFileId())
                .name(doc.getName())
                .type(doc.getType())
                .sizeBytes(doc.getSizeBytes())
                .ownerId(doc.getOwnerId())
                .teamId(doc.getTeamId())
                .storageKey(doc.getStorageKey())
                .fileUrl(doc.getFileUrl())
                .deletedAt(doc.getDeletedAt())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

}
