package com.docusphere.backend.document.controller;

import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.service.DocumentUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentUpdateController {

    private final DocumentUpdateService documentUpdateService;
    private final DocumentRepository documentRepository;

    @GetMapping("/{documentId}")
    public ResponseEntity<Document> getDocument(@PathVariable UUID documentId) {
        return documentRepository.findById(documentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{documentId}/edit")
    public ResponseEntity<Map<String, Object>> updateDocument(
            @PathVariable UUID documentId,
            @RequestParam("file") MultipartFile file) throws IOException {

        Document updatedDoc = documentUpdateService.saveEditedDocument(documentId, file);

        Map<String, Object> response = new HashMap<>();
        response.put("documentId", updatedDoc.getId());
        response.put("newStorageKey", updatedDoc.getStorageKey());
        response.put("fileName", updatedDoc.getName());
        response.put("updatedAt", updatedDoc.getUpdatedAt());

        return ResponseEntity.ok(response);
    }
}
