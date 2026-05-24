package com.docusphere.backend.documentStar.service;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentStar.dto.DocumentStarResponse;
import com.docusphere.backend.documentStar.entity.DocumentStar;
import com.docusphere.backend.documentStar.repository.DocumentStarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class DocumentStarService {

    private final DocumentRepository documentRepository;
    private final DocumentStarRepository starRepository;

    public DocumentStarService(DocumentRepository documentRepository,
                               DocumentStarRepository starRepository) {
        this.documentRepository = documentRepository;
        this.starRepository = starRepository;
    }

    @Transactional
    public DocumentStarResponse star(Long userId, UUID documentId) {

        validateDocument(documentId);

        DocumentStar star = starRepository.findByUserIdAndDocumentId(userId, documentId)
                .orElseGet(() -> starRepository.save(
                        DocumentStar.builder()
                                .userId(userId)
                                .documentId(documentId)
                                .build()
                ));

        return buildResponse(userId, documentId, true, star.getStarredAt());
    }

    @Transactional
    public DocumentStarResponse unstar(Long userId, UUID documentId) {

        validateDocument(documentId);

        starRepository.deleteByUserIdAndDocumentId(userId, documentId);

        return buildResponse(userId, documentId, false, null);
    }

    private void validateDocument(UUID documentId) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found"));
    }

    private DocumentStarResponse buildResponse(Long userId,
                                               UUID documentId,
                                               boolean starred,
                                               java.time.LocalDateTime starredAt) {
        return DocumentStarResponse.builder()
                .userId(userId)
                .documentId(documentId)
                .starred(starred)
                .starredAt(starredAt)
                .build();
    }
}