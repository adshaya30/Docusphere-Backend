package com.docusphere.backend.user.services;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.service.DocumentService;
import com.docusphere.backend.team.document.dto.DocumentSummaryDto;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.service.TeamService;

import lombok.extern.slf4j.Slf4j;

/**
 * Partner's service for leader/manager/member document access.
 * Enforces role-based access rules on top of shared DocumentService.
 *
 * Access matrix:
 *   LEADER / MANAGER / MEMBER → view all documents in their team
 *   LEADER / MANAGER          → edit, delete all documents in their team
 *   MEMBER                    → edit, delete only their own uploads
 *   Personal docs             → owner only
 */
@Service
@Slf4j
public class UserDocumentService {

    private final DocumentService documentService;
    private final TeamService teamService;
    private final UserRepository userRepository;

    public UserDocumentService(DocumentService documentService,
                               TeamService teamService,
                               UserRepository userRepository) {
        this.documentService = documentService;
        this.teamService = teamService;
        this.userRepository = userRepository;
    }

    public List<DocumentSummaryDto> getAccessibleDocuments(Long userId, UUID teamId) {
        // Everyone in the team (Leader, Manager, Member) can see all team documents
        List<Document> documents = fetchDocumentsSafely(
                () -> documentService.getDocumentsByTeam(teamId), 
                teamId, 
                userId
        );

        return mapDocumentsSafely(documents);
    }

    public void deleteDocument(UUID documentId, Long userId, UUID teamId) {
        boolean isPrivileged = teamService.isUserRoleInTeam(userId, teamId, TeamRole.LEADER) ||
                               teamService.isUserRoleInTeam(userId, teamId, TeamRole.MANAGER);

        if (isPrivileged || documentService.isOwner(documentId, userId)) {
            documentService.deleteById(documentId);
        } else {
            throw new IllegalStateException("You do not have permission to delete this document");
        }
    }

    private List<DocumentSummaryDto> mapDocumentsSafely(List<Document> documents) {
        return documents.stream()
                .map(this::safeToSummaryDto)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<Document> fetchDocumentsSafely(DocumentFetcher fetcher, UUID teamId, Long userId) {
        try {
            return fetcher.fetch();
        } catch (Exception ex) {
            log.warn("Failed to fetch team documents. Returning empty list. teamId={}, userId={}", teamId, userId, ex);
            return List.of();
        }
    }

    private DocumentSummaryDto safeToSummaryDto(Document document) {
        try {
            return toSummaryDto(document);
        } catch (Exception ex) {
            log.warn("Skipping malformed document row while building team documents response. documentId={}",
                    document != null ? document.getId() : null, ex);
            return null;
        }
    }

    private DocumentSummaryDto toSummaryDto(Document doc) {
        Long ownerId = doc.getOwnerId();
        String uploaderName = ownerId == null
            ? "Unknown"
            : userRepository.findById(ownerId)
                .map(u -> u.getFullName())
                .orElse("Unknown");

        return DocumentSummaryDto.builder()
                .id(doc.getId())
                .name(doc.getName())
                .type(doc.getType())
                .sizeBytes(doc.getSizeBytes())
            .ownerId(ownerId)
                .uploadedBy(uploaderName)
                .teamId(doc.getTeamId())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    @FunctionalInterface
    private interface DocumentFetcher {
        List<Document> fetch();
    }
}