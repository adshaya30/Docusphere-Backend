package com.docusphere.backend.documentAction.controller;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.documentAction.dto.DocumentActionResponse;
import com.docusphere.backend.documentAction.dto.MoveRequest;
import com.docusphere.backend.documentAction.dto.RenameRequest;
import com.docusphere.backend.documentAction.dto.TrashDocumentsPageResponse;
import com.docusphere.backend.documentAction.service.DocumentActionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
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
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
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
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
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
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
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
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
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
            HttpServletRequest request
    ) {
        Long requesterId = extractRequesterId(request);
        service.permanentlyDelete(requesterId, documentId);
        return ResponseEntity.ok(ApiResponse.success("Document permanently deleted", null));
    }

    @GetMapping("/trash")
    public ResponseEntity<ApiResponse<TrashDocumentsPageResponse>> getTrashDocuments(
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "15") @Min(1) @Max(100) int size,
            HttpServletRequest request
    ) {
        Long requesterId = extractRequesterId(request);
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
            @RequestParam(value = "token", required = false) String shareToken,
            @RequestParam(value = "password", required = false) String password,
            @RequestHeader(value = "X-Document-Password", required = false) String passwordHeader,
            HttpServletRequest request
    ) {
        String effectivePassword = resolvePassword(password, passwordHeader);
        Resource resource;
        String fileName;

        if (shareToken != null && !shareToken.isBlank()) {
            resource = service.downloadByShareToken(documentId, shareToken, effectivePassword);
            fileName = service.resolveDownloadFilenameByShareToken(documentId, shareToken, effectivePassword);
        } else {
            Long requesterId = extractRequesterId(request);
            resource = service.download(requesterId, documentId, effectivePassword);
            fileName = service.resolveDownloadFilename(requesterId, documentId, effectivePassword);
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    private String resolvePassword(String password, String passwordHeader) {
        if (password != null && !password.isBlank()) {
            return password;
        }
        if (passwordHeader != null && !passwordHeader.isBlank()) {
            return passwordHeader;
        }
        return null;
    }

    private Long extractRequesterId(HttpServletRequest request) {
        String token = resolveAccessToken(request);
        if (token == null || token.isBlank()) {
            throw new InvalidRequestException("Authorization header or accessToken cookie is required");
        }
        return jwtService.extractUserId(token);
    }

    private String resolveAccessToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        if (request.getCookies() == null) {
            return null;
        }

        for (Cookie cookie : request.getCookies()) {
            if ("accessToken".equalsIgnoreCase(cookie.getName())
                    || "jwt".equalsIgnoreCase(cookie.getName())
                    || "Authorization".equalsIgnoreCase(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}
