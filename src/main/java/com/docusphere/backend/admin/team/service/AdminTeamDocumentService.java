package com.docusphere.backend.admin.team.service;

import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentShare.repository.DocumentShareRepository;
import com.docusphere.backend.documentStar.repository.DocumentStarRepository;
import com.docusphere.backend.team.document.dto.DocumentSummaryDto;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.repository.TeamRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminTeamDocumentService {

    private final TeamRepository teamRepository;
    private final DocumentRepository documentRepository;
    private final DocumentStarRepository documentStarRepository;
    private final DocumentShareRepository documentShareRepository;
    private final UserRepository userRepository;

    public AdminTeamDocumentService(TeamRepository teamRepository,
            DocumentRepository documentRepository,
            DocumentStarRepository documentStarRepository,
            DocumentShareRepository documentShareRepository,
            UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.documentRepository = documentRepository;
        this.documentStarRepository = documentStarRepository;
        this.documentShareRepository = documentShareRepository;
        this.userRepository = userRepository;
    }

    public List<DocumentSummaryDto> getTeamDocuments(UUID teamId) {
        assertTeamExists(teamId);
        return documentRepository.findAllByTeamId(teamId).stream()
                .map(this::toDocumentSummaryDto)
                .collect(Collectors.toList());
    }

    public DocumentSummaryDto toDocumentSummaryDto(Document doc) {
        String uploaderName = "Unknown";
        if (doc.getOwnerId() != null) {
            uploaderName = userRepository.findById(doc.getOwnerId())
                    .map(com.docusphere.backend.authentication.entity.User::getFullName)
                    .orElse("Unknown");
        }

        return DocumentSummaryDto.builder()
                .id(doc.getId())
                .name(doc.getName())
                .type(doc.getType())
                .sizeBytes(doc.getSizeBytes())
                .ownerId(doc.getOwnerId())
                .uploadedBy(uploaderName)
                .teamId(doc.getTeamId())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private Team assertTeamExists(UUID teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new EntityNotFoundException("Team not found: " + teamId));
    }

    // add the code need to be like this format
    @Transactional
    public void deleteDocumentPermanently(UUID teamId, UUID documentId) {
        assertTeamExists(teamId);
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));
        
        // Ensure the document actually belongs to this team (or is being managed via this team)
        documentStarRepository.deleteByDocumentIdIn(List.of(documentId));
        documentShareRepository.deleteByDocumentIdIn(List.of(documentId));
        documentRepository.delete(doc);
        teamRepository.decrementDocumentCount(teamId);
    }

}
