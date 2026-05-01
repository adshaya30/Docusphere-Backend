package com.docusphere.backend.admin.document.controller;

import com.docusphere.backend.admin.document.dto.AdminDocumentView;
import com.docusphere.backend.admin.document.services.AdminDocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/documents")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDocumentController {

    private final AdminDocumentService adminDocumentService;

    public AdminDocumentController(AdminDocumentService adminDocumentService) {
        this.adminDocumentService = adminDocumentService;
    }

    // GET /api/admin/documents — all documents across all teams
    @GetMapping
    public ResponseEntity<List<AdminDocumentView>> getAllDocuments() {
        return ResponseEntity.ok(adminDocumentService.getAllDocuments());
    }

    // GET /api/admin/documents?teamId=... — all documents for a specific team
    @GetMapping(params = "teamId")
    public ResponseEntity<List<AdminDocumentView>> getDocumentsByTeam(
            @RequestParam UUID teamId) {
        return ResponseEntity.ok(adminDocumentService.getDocumentsByTeam(teamId));
    }

    // GET /api/admin/documents/{documentId}
    @GetMapping("/{documentId}")
    public ResponseEntity<AdminDocumentView> getDocument(
            @PathVariable UUID documentId) {
        return ResponseEntity.ok(adminDocumentService.getDocument(documentId));
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID documentId) {
        adminDocumentService.deleteDocument(documentId);
        return ResponseEntity.noContent().build();
    }
}