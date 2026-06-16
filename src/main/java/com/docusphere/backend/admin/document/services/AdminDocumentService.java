package com.docusphere.backend.admin.document.services;


import com.docusphere.backend.admin.document.dto.AdminDocumentView;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.service.DocumentService;

import com.docusphere.backend.team.repository.TeamRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminDocumentService {

    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    public AdminDocumentService(DocumentService documentService,
                                DocumentRepository documentRepository,
                                UserRepository userRepository,
                                TeamRepository teamRepository) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
    }

    // ── View all documents (cross-team, admin only) ──────────────────────────

    public List<AdminDocumentView> getAllDocuments() {
        return documentRepository.findAll()
                .stream()
                .map(this::toAdminView)
                .collect(Collectors.toList());
    }

    public List<AdminDocumentView> getDocumentsByTeam(UUID teamId) {
        return documentRepository.findAllByTeamId(teamId)
                .stream()
                .map(this::toAdminView)
                .collect(Collectors.toList());
    }

    public AdminDocumentView getDocument(UUID documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("Document not found: " + documentId));
        return toAdminView(doc);
    }

    // ── Delete any document (admin bypasses ownership check) ─────────────────

    @Transactional
    public void deleteDocument(UUID documentId) {
        if (documentRepository.findById(documentId).isEmpty()) {
            throw new EntityNotFoundException("Document not found: " + documentId);
        }
        documentService.deleteById(documentId);
    }

    // ── Mapper ───────────────────────────────────────────────────────────────

    private AdminDocumentView toAdminView(Document doc) {
        AdminDocumentView view = new AdminDocumentView();
        view.setId(doc.getId());
        view.setFileId(doc.getFileId());
        view.setName(doc.getName());
        view.setType(doc.getType());
        view.setSizeBytes(doc.getSizeBytes());
        view.setOwnerId(doc.getOwnerId());
        view.setTeamId(doc.getTeamId());
        view.setStatus(doc.getStatus().name());
        view.setSecured(doc.isSecured());
        view.setStorageKey(doc.getStorageKey());
        view.setFileUrl(doc.getFileUrl());
        view.setCreatedAt(doc.getCreatedAt());
        view.setUpdatedAt(doc.getUpdatedAt());
        view.setUpdatedAt(doc.getUpdatedAt());

        // Enrich with owner info
        userRepository.findById(doc.getOwnerId()).ifPresent(user -> {
            view.setOwnerEmail(user.getEmail());
            view.setOwnerFullName(user.getFullName());
        });

        // Enrich with team name
        if (doc.getTeamId() != null) {
            teamRepository.findById(doc.getTeamId()).ifPresent(team ->
                    view.setTeamName(team.getTeamName()));
        }

        return view;
    }
}