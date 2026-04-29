package com.docusphere.backend.team.document.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.team.document.dto.DocumentSummaryDto;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.service.TeamService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class TeamDocumentService {

    private final DocumentRepository documentRepository;
    private final TeamService teamService;
    private final UserRepository userRepository;

    public TeamDocumentService(DocumentRepository documentRepository,
                               TeamService teamService,
                               UserRepository userRepository) {
        this.documentRepository = documentRepository;
        this.teamService = teamService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<DocumentSummaryDto> getTeamDocuments(Long userId, UUID teamId) {
        // Everyone in the team (Leader, Manager, Member) can see all team documents
        List<Document> documents = documentRepository.findByTeamIdAndDeletedFalse(teamId, Pageable.unpaged()).getContent();
        
        // Batch fetch uploader names to avoid N+1 problem
        List<Long> ownerIds = documents.stream()
                .map(Document::getOwnerId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        
        java.util.Map<Long, String> uploaderNames = userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(com.docusphere.backend.authentication.entity.User::getId, com.docusphere.backend.authentication.entity.User::getFullName));

        return documents.stream()
            .map(doc -> toSummaryDto(doc, uploaderNames))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteTeamDocument(UUID documentId, Long userId, UUID teamId) {
        boolean isPrivileged = teamService.isUserRoleInTeam(userId, teamId, TeamRole.LEADER) ||
                               teamService.isUserRoleInTeam(userId, teamId, TeamRole.MANAGER);

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalStateException("Document not found"));

        if (isPrivileged || document.getOwnerId().equals(userId)) {
            documentRepository.delete(document);
            // Note: Storage cleanup should be handled here or via an event/cleanup service
        } else {
            throw new IllegalStateException("You do not have permission to delete this document");
        }
    }

    private DocumentSummaryDto toSummaryDto(Document doc, java.util.Map<Long, String> uploaderNames) {
        try {
            Long ownerId = doc.getOwnerId();
            String uploaderName = uploaderNames.getOrDefault(ownerId, "Unknown");

            return DocumentSummaryDto.builder()
                    .id(doc.getId())
                    .name(doc.getName())
                    .type(doc.getType())
                    .sizeBytes(doc.getSizeBytes())
                    .starred(false)
                    .ownerId(ownerId)
                    .uploadedBy(uploaderName)
                    .teamId(doc.getTeamId())
                    .createdAt(doc.getCreatedAt())
                    .updatedAt(doc.getUpdatedAt())
                    .build();
        } catch (Exception ex) {
            log.warn("Error mapping document to DTO: {}", doc.getId(), ex);
            return null;
        }
    }
}
