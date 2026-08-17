package com.docusphere.backend.documentShare.controller;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.comment.entity.Comment;
import com.docusphere.backend.comment.repository.CommentRepository;
import com.docusphere.backend.documentShare.dto.CreateShareLinkRequest;
import com.docusphere.backend.documentShare.dto.CreateShareLinkResponse;
import com.docusphere.backend.documentShare.dto.DocumentShareListItemResponse;
import com.docusphere.backend.documentShare.dto.ShareCommentRequest;
import com.docusphere.backend.documentShare.dto.SharedDocumentResponse;
import com.docusphere.backend.documentShare.service.DocumentSharingService;
import com.docusphere.backend.onlyoffice.dto.SharedEditorConfigResponse;
import com.docusphere.backend.onlyoffice.service.OnlyOfficeEditorConfigService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@Validated
public class DocumentSharingController {

    private final DocumentSharingService documentSharingService;
    private final OnlyOfficeEditorConfigService onlyOfficeEditorConfigService;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public DocumentSharingController(
            DocumentSharingService documentSharingService,
            OnlyOfficeEditorConfigService onlyOfficeEditorConfigService,
            CommentRepository commentRepository,
            UserRepository userRepository,
            JwtService jwtService
    ) {
        this.documentSharingService = documentSharingService;
        this.onlyOfficeEditorConfigService = onlyOfficeEditorConfigService;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/api/documents/{id}/share")
    public ResponseEntity<ApiResponse<CreateShareLinkResponse>> createShareLink(
            @PathVariable("id") UUID documentId,
            @Valid @RequestBody CreateShareLinkRequest request,
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
        CreateShareLinkResponse response = documentSharingService.createShareLink(requesterId, documentId, request);
        return ResponseEntity.ok(ApiResponse.success("Share link created successfully", response));
    }

    @GetMapping("/api/documents/{id}/shares")
    public ResponseEntity<ApiResponse<List<DocumentShareListItemResponse>>> listShareLinks(
            @PathVariable("id") UUID documentId,
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
        List<DocumentShareListItemResponse> shares = documentSharingService.listActiveShareLinks(requesterId, documentId);
        return ResponseEntity.ok(ApiResponse.success("Active share links fetched successfully", shares));
    }

    @DeleteMapping("/api/documents/{id}/share")
    public ResponseEntity<ApiResponse<Void>> revokeShareLink(
            @PathVariable("id") UUID documentId,
            @RequestParam("token") String shareToken,
            HttpServletRequest httpRequest
    ) {
        Long requesterId = extractRequesterId(httpRequest);
        documentSharingService.revokeShareLink(requesterId, documentId, shareToken);
        return ResponseEntity.ok(ApiResponse.success("Share link revoked successfully", null));
    }

    @GetMapping("/api/share/{token}")
    public ResponseEntity<ApiResponse<SharedDocumentResponse>> openSharedDocument(
            @PathVariable("token") String shareToken
    ) {
        SharedDocumentResponse response = documentSharingService.openSharedDocument(shareToken);
        return ResponseEntity.ok(ApiResponse.success("Shared document fetched successfully", response));
    }

    /**
     * Compatibility endpoint used by the shared document page.
     * Returns the same ONLYOFFICE config as GET /api/editor/documents/{id}?token=...
     */
    @GetMapping("/api/share/{token}/editor-config")
    public ResponseEntity<ApiResponse<SharedEditorConfigResponse>> getSharedEditorConfig(
            @PathVariable("token") String shareToken,
            @RequestParam(value = "password", required = false) String password,
            @RequestHeader(value = "X-Document-Password", required = false) String passwordHeader,
            HttpServletRequest request
    ) {
        SharedDocumentResponse shared = documentSharingService.openSharedDocument(shareToken);
        String effectivePassword = password != null && !password.isBlank() ? password : passwordHeader;
        var config = onlyOfficeEditorConfigService.buildEditorConfig(
                shared.getDocumentId(),
                shareToken,
                effectivePassword,
                request
        );
        SharedEditorConfigResponse response = SharedEditorConfigResponse.builder()
                .documentId(shared.getDocumentId())
                .invitedEmail(shared.getInvitedEmail())
                .config(config)
                .build();
        return ResponseEntity.ok(ApiResponse.success("Shared editor config fetched successfully", response));
    }

    @GetMapping("/api/share/{token}/comments")
    public ResponseEntity<ApiResponse<List<Comment>>> getShareComments(
            @PathVariable("token") String shareToken
    ) {
        SharedDocumentResponse shared = documentSharingService.openSharedDocument(shareToken);
        List<Comment> comments = commentRepository.findByDocumentIdOrderByTimestampAsc(shared.getDocumentId());
        return ResponseEntity.ok(ApiResponse.success("Comments fetched successfully", comments));
    }

    @PostMapping("/api/share/{token}/comments")
    public ResponseEntity<ApiResponse<Comment>> addShareComment(
            @PathVariable("token") String shareToken,
            @RequestBody ShareCommentRequest body
    ) {
        if (body == null || body.getMessage() == null || body.getMessage().isBlank()) {
            throw new InvalidRequestException("message is required");
        }

        SharedDocumentResponse shared = documentSharingService.openSharedDocument(shareToken);
        documentSharingService.requireCommentPermission(shared.getDocumentId(), shareToken);
        documentSharingService.recordShareComment(shared.getDocumentId(), shareToken);

        String authorEmail = shared.getInvitedEmail();
        Long userId = null;
        if (authorEmail != null && !authorEmail.isBlank()) {
            userId = userRepository.findByEmail(authorEmail.toLowerCase())
                    .map(user -> user.getId())
                    .orElse(null);
        }

        Comment comment = Comment.builder()
                .documentId(shared.getDocumentId())
                .userId(userId)
                .authorEmail(authorEmail)
                .message(body.getMessage().trim())
                .timestamp(LocalDateTime.now())
                .build();

        Comment saved = commentRepository.save(comment);
        return ResponseEntity.ok(ApiResponse.success("Comment added successfully", saved));
    }

    private Long extractRequesterId(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                if ("accessToken".equalsIgnoreCase(c.getName()) || "jwt".equalsIgnoreCase(c.getName()) || "Authorization".equalsIgnoreCase(c.getName())) {
                    token = c.getValue();
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
