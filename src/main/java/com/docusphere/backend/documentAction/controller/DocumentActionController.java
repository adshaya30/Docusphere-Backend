package com.docusphere.backend.documentAction.controller;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.documentAction.dto.DocumentActionResponse;
import com.docusphere.backend.documentAction.dto.MoveRequest;
import com.docusphere.backend.documentAction.dto.RenameRequest;
import com.docusphere.backend.documentAction.dto.TrashDocumentsPageResponse;
import com.docusphere.backend.documentAction.service.DocumentActionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@Validated
public class DocumentActionController {

    private final DocumentActionService service;
    private final JwtService jwtService;

    public DocumentActionController(DocumentActionService service, JwtService jwtService) {
        this.service = service;
        this.jwtService = jwtService;
    }

    @PutMapping("/{id}/rename")
    public ResponseEntity<ApiResponse<DocumentActionResponse>> rename(
            @PathVariable("id") UUID documentId,
            @Valid @RequestBody RenameRequest request,
            @RequestHeader("Authorization") String token
    ) {
        Long requesterId = extractRequesterId(token);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Document renamed",
                        service.rename(requesterId, documentId, request.getNewName())
                )
        );
    }

    @PutMapping("/{id}/move")
    public ResponseEntity<ApiResponse<DocumentActionResponse>> move(
            @PathVariable("id") UUID documentId,
            @Valid @RequestBody MoveRequest request,
            @RequestHeader("Authorization") String token
    ) {
        Long requesterId = extractRequesterId(token);
        UUID targetTeamId = request.getTeamId() == null || request.getTeamId().isBlank()
                ? null
                : UUID.fromString(request.getTeamId().trim());

        return ResponseEntity.ok(
                ApiResponse.success(
                        "Document moved",
                        service.move(requesterId, documentId, targetTeamId)
                )
        );
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ApiResponse<DocumentActionResponse>> duplicate(
            @PathVariable("id") UUID documentId,
            @RequestHeader("Authorization") String token
    ) {
        Long requesterId = extractRequesterId(token);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Document duplicated",
                        service.duplicate(requesterId, documentId)
                )
        );
    }

    @DeleteMapping("/{id}/trash")
    public ResponseEntity<ApiResponse<DocumentActionResponse>> moveToTrash(
            @PathVariable("id") UUID documentId,
            @RequestHeader("Authorization") String token
    ) {
        Long requesterId = extractRequesterId(token);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Document moved to trash",
                        service.moveToTrash(requesterId, documentId)
                )
        );
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<ApiResponse<DocumentActionResponse>> restoreFromTrash(
            @PathVariable("id") UUID documentId,
            @RequestHeader("Authorization") String token
    ) {
        Long requesterId = extractRequesterId(token);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Document restored from trash",
                        service.restoreFromTrash(requesterId, documentId)
                )
        );
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<ApiResponse<Void>> permanentlyDelete(
            @PathVariable("id") UUID documentId,
            @RequestHeader("Authorization") String token
    ) {
        Long requesterId = extractRequesterId(token);
        service.permanentlyDelete(requesterId, documentId);
        return ResponseEntity.ok(ApiResponse.success("Document permanently deleted", null));
    }

    @GetMapping("/trash")
    public ResponseEntity<ApiResponse<TrashDocumentsPageResponse>> getTrashDocuments(
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "15") @Min(1) @Max(100) int size,
            @RequestHeader("Authorization") String token
    ) {
        Long requesterId = extractRequesterId(token);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Trash documents fetched successfully",
                        service.getTrash(requesterId, page, size)
                )
        );
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(
            @PathVariable("id") UUID documentId,
            @RequestHeader(value = "Authorization", required = false) String token,
            @RequestParam(value = "token", required = false) String shareToken
    ) {
        Resource resource;
        String fileName;

        if (shareToken != null && !shareToken.isBlank()) {
            resource = service.downloadByShareToken(documentId, shareToken);
            fileName = service.resolveDownloadFilenameByShareToken(documentId, shareToken);
        } else {
            Long requesterId = extractRequesterId(token);
            resource = service.download(requesterId, documentId);
            fileName = service.resolveDownloadFilename(requesterId, documentId);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    private Long extractRequesterId(String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            throw new InvalidRequestException("Authorization header with Bearer token is required");
        }
        return jwtService.extractUserId(token.substring(7));
    }
}