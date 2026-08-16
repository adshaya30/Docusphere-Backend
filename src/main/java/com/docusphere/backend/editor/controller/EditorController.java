package com.docusphere.backend.editor.controller;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.editor.dto.CreateEditorDocumentRequest;
import com.docusphere.backend.editor.dto.SaveEditorDocumentRequest;
import com.docusphere.backend.editor.entity.EditorDocument;
import com.docusphere.backend.editor.service.EditorService;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeCallback;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/editor")
@Validated
public class EditorController {

    private final EditorService editorService;
    private final JwtService jwtService;

    public EditorController(EditorService editorService, JwtService jwtService) {
        this.editorService = editorService;
        this.jwtService = jwtService;
    }

    /**
     * POST /api/editor/create
     * Purpose: Create a new empty editor document and return ONLYOFFICE config.
     */
    @PostMapping("/create")
    public ResponseEntity<ApiResponse<OnlyOfficeConfig>> createDocument(
            @Valid @RequestBody CreateEditorDocumentRequest request,
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
        OnlyOfficeConfig config = editorService.createDocument(requesterId, request);
        return ResponseEntity.ok(ApiResponse.success("Document created successfully", config));
    }

    /**
     * GET /api/editor/list
     * Purpose: Return all documents belonging to the authenticated user.
     */
    @GetMapping("/list")
    public ResponseEntity<ApiResponse<List<EditorDocument>>> listDocuments(
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
        List<EditorDocument> documents = editorService.listDocuments(requesterId);
        return ResponseEntity.ok(ApiResponse.success("Documents fetched successfully", documents));
    }

    /**
     * GET /api/editor/{id}
     * Purpose: Open existing document and return ONLYOFFICE editor configuration.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OnlyOfficeConfig>> getDocument(
            @PathVariable("id") UUID documentId,
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
        OnlyOfficeConfig config = editorService.getDocumentConfig(requesterId, documentId);
        return ResponseEntity.ok(ApiResponse.success("Document configuration fetched", config));
    }

    /**
     * PUT /api/editor/save
     * Purpose: Save metadata after editing.
     */
    @PutMapping("/save")
    public ResponseEntity<ApiResponse<EditorDocument>> saveMetadata(
            @Valid @RequestBody SaveEditorDocumentRequest request,
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
        EditorDocument updatedDoc = editorService.saveMetadata(requesterId, request);
        return ResponseEntity.ok(ApiResponse.success("Document metadata updated successfully", updatedDoc));
    }

    /**
     * DELETE /api/editor/{id}
     * Purpose: Delete document.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable("id") UUID documentId,
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
        editorService.deleteDocument(requesterId, documentId);
        return ResponseEntity.ok(ApiResponse.success("Document deleted successfully", null));
    }

    /**
     * POST /api/editor/callback
     * Purpose: Public ONLYOFFICE save callback URL.
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Integer>> handleCallback(
            @RequestParam("id") UUID documentId,
            @RequestParam("token") String token,
            @RequestBody OnlyOfficeCallback callback
    ) {
        log.info("Processing ONLYOFFICE save callback request for editor document ID: {}", documentId);
        try {
            editorService.handleSaveCallback(documentId, token, callback);
            return ResponseEntity.ok(Collections.singletonMap("error", 0));
        } catch (Exception e) {
            log.error("Failed to process ONLYOFFICE editor callback", e);
            return ResponseEntity.status(500).body(Collections.singletonMap("error", 1));
        }
    }

    // ──────────────────────────── Private Helpers ────────────────────────────────

    private Long extractRequesterId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("accessToken".equalsIgnoreCase(cookie.getName())
                        || "jwt".equalsIgnoreCase(cookie.getName())
                        || "Authorization".equalsIgnoreCase(cookie.getName())) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null || token.isBlank()) {
            throw new InvalidRequestException("Authorization header or accessToken cookie is required");
        }

        return jwtService.extractUserId(token);
    }
}
