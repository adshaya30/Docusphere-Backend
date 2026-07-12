package com.docusphere.backend.admin.report.controller;

import com.docusphere.backend.admin.report.service.AdminReportExportService;
import com.docusphere.backend.authentication.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.core.userdetails.UserDetailsService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminReportExportController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminReportExportController Unit Tests")
class AdminReportExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminReportExportService reportService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("Should export users report as PDF")
    void testExportUsersPDF_Success() throws Exception {
        byte[] content = "dummy-pdf-content".getBytes();
        when(reportService.exportUsersToPDF()).thenReturn(content);

        mockMvc.perform(get("/api/admin/reports/users/export-pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users_report.pdf"))
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(content));
    }

    @Test
    @DisplayName("Should export users report as CSV")
    void testExportUsersCSV_Success() throws Exception {
        byte[] content = "dummy-csv-content".getBytes();
        when(reportService.exportUsersToCSV()).thenReturn(content);

        mockMvc.perform(get("/api/admin/reports/users/export-csv"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users_report.csv"))
                .andExpect(content().contentType(MediaType.parseMediaType("text/csv")))
                .andExpect(content().bytes(content));
    }

    @Test
    @DisplayName("Should export users report as XLSX")
    void testExportUsersXLSX_Success() throws Exception {
        byte[] content = "dummy-xlsx-content".getBytes();
        when(reportService.exportUsersToXLSX()).thenReturn(content);

        mockMvc.perform(get("/api/admin/reports/users/export-xlsx"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=users_report.xlsx"))
                .andExpect(content().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(content().bytes(content));
    }

    @Test
    @DisplayName("Should export teams report as PDF")
    void testExportTeamsPDF_Success() throws Exception {
        byte[] content = "dummy-teams-pdf-content".getBytes();
        when(reportService.exportTeamsToPDF()).thenReturn(content);

        mockMvc.perform(get("/api/admin/reports/teams/export-pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=teams_report.pdf"));
    }

    @Test
    @DisplayName("Should export documents report as PDF")
    void testExportDocumentsPDF_Success() throws Exception {
        byte[] content = "dummy-docs-pdf-content".getBytes();
        when(reportService.exportDocumentsToPDF()).thenReturn(content);

        mockMvc.perform(get("/api/admin/reports/documents/export-pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=documents_report.pdf"));
    }
}
