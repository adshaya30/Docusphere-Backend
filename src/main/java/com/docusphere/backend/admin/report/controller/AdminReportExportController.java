package com.docusphere.backend.admin.report.controller;

import com.docusphere.backend.admin.report.service.AdminReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Reports", description = "Export reports in PDF, CSV, and XLSX formats")
public class AdminReportExportController {

    private final AdminReportExportService reportService;

    public AdminReportExportController(AdminReportExportService reportService) {
        this.reportService = reportService;
    }

    // ==================== USERS ====================

    @GetMapping("/users/export-pdf")
    @Operation(summary = "Export all users as PDF")
    public ResponseEntity<byte[]> exportUsersPDF() throws Exception {
        byte[] content = reportService.exportUsersToPDF();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users_report.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(content);
    }

    @GetMapping("/users/export-csv")
    @Operation(summary = "Export all users as CSV")
    public ResponseEntity<byte[]> exportUsersCSV() throws Exception {
        byte[] content = reportService.exportUsersToCSV();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users_report.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(content);
    }

    @GetMapping("/users/export-xlsx")
    @Operation(summary = "Export all users as XLSX")
    public ResponseEntity<byte[]> exportUsersXLSX() throws Exception {
        byte[] content = reportService.exportUsersToXLSX();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users_report.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(content);
    }

    // ==================== TEAMS ====================

    @GetMapping("/teams/export-pdf")
    @Operation(summary = "Export all teams as PDF")
    public ResponseEntity<byte[]> exportTeamsPDF() throws Exception {
        byte[] content = reportService.exportTeamsToPDF();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=teams_report.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(content);
    }

    @GetMapping("/teams/export-csv")
    @Operation(summary = "Export all teams as CSV")
    public ResponseEntity<byte[]> exportTeamsCSV() throws Exception {
        byte[] content = reportService.exportTeamsToCSV();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=teams_report.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(content);
    }

    @GetMapping("/teams/export-xlsx")
    @Operation(summary = "Export all teams as XLSX")
    public ResponseEntity<byte[]> exportTeamsXLSX() throws Exception {
        byte[] content = reportService.exportTeamsToXLSX();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=teams_report.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(content);
    }

    // ==================== DOCUMENTS ====================

    @GetMapping("/documents/export-pdf")
    @Operation(summary = "Export all documents as PDF")
    public ResponseEntity<byte[]> exportDocumentsPDF() throws Exception {
        byte[] content = reportService.exportDocumentsToPDF();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=documents_report.pdf")
            .contentType(MediaType.APPLICATION_PDF)
            .body(content);
    }

    @GetMapping("/documents/export-csv")
    @Operation(summary = "Export all documents as CSV")
    public ResponseEntity<byte[]> exportDocumentsCSV() throws Exception {
        byte[] content = reportService.exportDocumentsToCSV();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=documents_report.csv")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(content);
    }

    @GetMapping("/documents/export-xlsx")
    @Operation(summary = "Export all documents as XLSX")
    public ResponseEntity<byte[]> exportDocumentsXLSX() throws Exception {
        byte[] content = reportService.exportDocumentsToXLSX();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=documents_report.xlsx")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(content);
    }
}
