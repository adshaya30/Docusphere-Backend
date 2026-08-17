package com.docusphere.backend.myDocuments.service;

import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentStar.entity.DocumentStar;
import com.docusphere.backend.documentStar.repository.DocumentStarRepository;
import com.docusphere.backend.myDocuments.dto.MyDocumentItemResponse;
import com.docusphere.backend.myDocuments.dto.MyDocumentsPageResponse;
import com.docusphere.backend.myDocuments.specification.MyDocumentsSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MyDocumentsService {

    private final DocumentRepository documentRepository;
    private final DocumentStarRepository starRepository;

    public MyDocumentsService(DocumentRepository documentRepository,
                              DocumentStarRepository starRepository) {
        this.documentRepository = documentRepository;
        this.starRepository = starRepository;
    }

    public MyDocumentsPageResponse getDocuments(
            Long ownerId,
            UUID teamId,
            int page,
            int size,
            String sortBy,
            String sortDirection,
            String type,
            Boolean starred,
            String search
    ) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.fromString(sortDirection), sortBy));


        Set<UUID> starredDocs = starRepository.findByUserId(ownerId)
                .stream()
                .map(DocumentStar::getDocumentId)
                .collect(Collectors.toSet());

        Specification<Document> spec = MyDocumentsSpecifications.hasOwner(ownerId)
                .and(MyDocumentsSpecifications.isNotDeleted());

        if (teamId == null) {
            spec = spec.and(MyDocumentsSpecifications.isUserSpace());
        } else {
            spec = spec.and(MyDocumentsSpecifications.hasTeamId(teamId));
        }

        if (type != null && !type.isBlank()) {
            spec = spec.and(MyDocumentsSpecifications.hasType(type));
        }

        if (search != null) {
            spec = spec.and(MyDocumentsSpecifications.hasSearch(search));
        }

        if (starred != null) {
            spec = spec.and((root, q, cb) ->
                    starred
                            ? root.get("id").in(starredDocs)
                            : cb.not(root.get("id").in(starredDocs))
            );
        }

        Page<Document> result = documentRepository.findAll(spec, pageable);

        return MyDocumentsPageResponse.builder()
                .items(result.getContent().stream()
                        .map(doc -> toDTO(doc, starredDocs))
                        .toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .empty(result.isEmpty())
                .build();
    }

    private MyDocumentItemResponse toDTO(Document doc, Set<UUID> starredDocs) {
        return MyDocumentItemResponse.builder()
                .id(doc.getId().toString())
                .fileId(doc.getFileId())
                .name(doc.getName())
                .type(doc.getType())
                .sizeBytes(doc.getSizeBytes())
                .ownerId(doc.getOwnerId().toString())
                .teamId(doc.getTeamId() != null ? doc.getTeamId().toString() : null)
                .starred(starredDocs.contains(doc.getId()))
                .secured(doc.isSecured())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .status(MyDocumentItemResponse.UploadStatus.valueOf(doc.getStatus().name()))
                .build();
    }
}