package com.docusphere.backend.documentStar.controller;

import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.documentStar.dto.DocumentStarResponse;
import com.docusphere.backend.documentStar.service.DocumentStarService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentStarController {

    private final DocumentStarService service;

    public DocumentStarController(DocumentStarService service) {
        this.service = service;
    }

    @PostMapping("/{documentId}/star")
    public ResponseEntity<ApiResponse<DocumentStarResponse>> star(
            @PathVariable UUID documentId,
            @RequestParam Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("Starred", service.star(userId, documentId))
        );
    }

    @DeleteMapping("/{documentId}/star")
    public ResponseEntity<ApiResponse<DocumentStarResponse>> unstar(
            @PathVariable UUID documentId,
            @RequestParam Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success("Unstarred", service.unstar(userId, documentId))
        );
    }
}