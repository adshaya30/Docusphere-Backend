package com.docusphere.backend.myDocuments.controller;

import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.myDocuments.dto.MyDocumentsPageResponse;
import com.docusphere.backend.myDocuments.service.MyDocumentsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

@RestController
@RequestMapping("/api/my-documents")
@Validated
public class MyDocumentController {

    private final MyDocumentsService service;
    private final JwtService jwtService;

    public MyDocumentController(MyDocumentsService service, JwtService jwtService) {
        this.service = service;
        this.jwtService = jwtService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MyDocumentsPageResponse>> getDocuments(
            @RequestParam(value = "teamId", required = false) String teamId,
            @RequestParam(value = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(value = "size", defaultValue = "15") @Min(1) @Max(100) int size, // Controls the number of items fetched per page
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDirection", defaultValue = "DESC") String sortDirection,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "starred", required = false) Boolean starred,
            HttpServletRequest request
    )
    {
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

        Long ownerId = jwtService.extractUserId(token);
        UUID parsedTeamId = (teamId != null && !teamId.isBlank()) ? UUID.fromString(teamId.trim()) : null;

        MyDocumentsPageResponse response = service.getDocuments(
                ownerId, parsedTeamId, page, size, sortBy, sortDirection, type, starred, search
        );

        return ResponseEntity.ok(ApiResponse.success("Documents fetched successfully", response));
    }
}
