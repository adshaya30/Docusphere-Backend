package com.docusphere.backend.document.service;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.documentStar.dto.DocumentStarResponse;
import com.docusphere.backend.documentStar.service.DocumentStarService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.storage.FileStorageService;

import java.util.UUID;
import java.util.List;

@Service
public class DocumentService {

    private final DocumentStarService documentStarService;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;


    public DocumentService(DocumentStarService documentStarService,
                           DocumentRepository documentRepository,
                           FileStorageService fileStorageService) {
        this.documentStarService = documentStarService;
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
    }
    @Transactional
    public DocumentStarResponse star(String userId, String documentId) {
        return documentStarService.star(
                parseLong(userId, "userId"),
                parseUuid(documentId, "documentId")
        );
    }

    @Transactional
    public DocumentStarResponse unstar(String userId, String documentId) {
        return documentStarService.unstar(
                parseLong(userId, "userId"),
                parseUuid(documentId, "documentId")
        );
    }

    // For Team Purpose
    @Transactional(readOnly = true)
    public List<Document> getDocumentsByTeam(UUID teamId) {
        return documentRepository.findByTeamIdAndDeletedFalse(teamId, Pageable.unpaged()).getContent();
    }

    @Transactional(readOnly = true)
    public List<Document> getDocumentsByOwnerInTeam(Long ownerId, UUID teamId) {
        return documentRepository.findByOwnerIdAndTeamIdAndDeletedFalse(ownerId, teamId, Pageable.unpaged()).getContent();
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UUID documentId, Long ownerId) {
        return documentRepository.findById(documentId)
                .map(document -> document.getOwnerId().equals(ownerId))
                .orElse(false);
    }

    @Transactional
    public void deleteById(UUID documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));

        try {
            fileStorageService.deleteFile(document.getStorageKey());
        } catch (Exception ex) {
            // Keep deletion moving if storage cleanup fails; the DB row is still removed.
        }

        documentRepository.delete(document);
    }


    private Long parseLong(String value, String fieldName) {
        String normalized = normalize(value, fieldName);
        try {
            return Long.parseLong(normalized);
        } catch (NumberFormatException ex) {
            throw new InvalidRequestException(fieldName + " must be a valid numeric id");
        }
    }

    private UUID parseUuid(String value, String fieldName) {
        String normalized = normalize(value, fieldName);
        try {
            return UUID.fromString(normalized);
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(fieldName + " must be a valid UUID");
        }
    }

    private String normalize(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(fieldName + " is required");
        }
        return value.trim();
    }
}
