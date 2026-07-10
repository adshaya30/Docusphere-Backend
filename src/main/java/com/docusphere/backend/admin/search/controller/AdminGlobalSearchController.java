package com.docusphere.backend.admin.search.controller;

import com.docusphere.backend.admin.search.dto.GlobalSearchResultDTO;
import com.docusphere.backend.admin.search.service.AdminGlobalSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/search")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Search", description = "Global search across users, teams, and documents")
public class AdminGlobalSearchController {

    private final AdminGlobalSearchService searchService;

    public AdminGlobalSearchController(AdminGlobalSearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/global")
    @Operation(summary = "Global search", description = "Search across all users, teams, and documents")
    public ResponseEntity<GlobalSearchResultDTO> globalSearch(@RequestParam String query) {
        GlobalSearchResultDTO results = searchService.globalSearch(query);
        return ResponseEntity.ok(results);
    }
}
