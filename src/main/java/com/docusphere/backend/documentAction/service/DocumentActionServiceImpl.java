package com.docusphere.backend.documentAction.service;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.documentAction.dto.DocumentActionResponse;
import com.docusphere.backend.documentAction.dto.TrashDocumentItemResponse;
import com.docusphere.backend.documentAction.dto.TrashDocumentsPageResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class DocumentActionServiceImpl implements DocumentActionService {

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final TeamAccessValidator teamAccessValidator;
    private final String supabaseUrl;
    private final String bucketName;

    public DocumentActionServiceImpl(
            DocumentRepository documentRepository,
            FileStorageService fileStorageService,
            TeamAccessValidator teamAccessValidator,
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.bucket.documents:documents}") String bucketName
    ) {
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.teamAccessValidator = teamAccessValidator;
        this.supabaseUrl = supabaseUrl;
        this.bucketName = bucketName;
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

        document.setTeamId(targetTeamId);
        return toResponse(documentRepository.save(document));
    }

    @Override
    @Transactional
    public DocumentActionResponse duplicate(Long requesterId, UUID documentId) {
        Document original = requireActiveDocument(documentId);
        ensureAccessible(original, requesterId);

        String duplicatedName = generateDuplicateName(original.getName(), original.getOwnerId(), original.getTeamId());
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
                .ownerId(original.getOwnerId())
                .teamId(original.getTeamId())
                .storageKey(duplicatedStorageKey)
                .fileUrl(generateNewUrl(duplicatedStorageKey))
                .status(original.getStatus())
                .secured(original.isSecured())
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
    public Resource download(Long requesterId, UUID documentId) {
        Document document = requireActiveDocument(documentId);
        ensureAccessible(document, requesterId);
        return new ByteArrayResource(fileStorageService.loadFile(document.getStorageKey()));
    }

    @Override
    @Transactional(readOnly = true)
    public String resolveDownloadFilename(Long requesterId, UUID documentId) {
        Document document = requireActiveDocument(documentId);
        ensureAccessible(document, requesterId);
        return document.getName();
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
