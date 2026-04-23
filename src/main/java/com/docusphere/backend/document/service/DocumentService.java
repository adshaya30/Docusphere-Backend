package com.docusphere.backend.document.service;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.documentStar.dto.DocumentStarResponse;
import com.docusphere.backend.documentStar.service.DocumentStarService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentStarService documentStarService;

    public DocumentService(DocumentStarService documentStarService) {
        this.documentStarService = documentStarService;
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
