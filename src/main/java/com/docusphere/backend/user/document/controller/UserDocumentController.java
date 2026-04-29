package com.docusphere.backend.user.document.controller;

import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.myDocuments.dto.MyDocumentsPageResponse;
import com.docusphere.backend.user.document.service.UserDocumentService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/documents")
@Validated
public class UserDocumentController {

    private final UserDocumentService userDocumentService;
    private final JwtService jwtService;

    public UserDocumentController(UserDocumentService userDocumentService, JwtService jwtService) {
        this.userDocumentService = userDocumentService;
        this.jwtService = jwtService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MyDocumentsPageResponse>> getUserDocuments(
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "15") @Min(1) @Max(100) int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "DESC") String sortDirection,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "starred", required = false) Boolean starred,
            @RequestHeader("Authorization") String token
    ) {
        Long userId = jwtService.extractUserId(token.substring(7));
        MyDocumentsPageResponse response = userDocumentService.getUserDocuments(
                userId, page, size, sortBy, sortDirection, type, starred, search
        );
        return ResponseEntity.ok(ApiResponse.success("User documents fetched successfully", response));
    }
}
