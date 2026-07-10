package com.docusphere.backend.documentVersion.controller;

import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.documentVersion.dto.DocumentVersionListResponse;
import com.docusphere.backend.documentVersion.dto.DocumentVersionResponse;
import com.docusphere.backend.documentVersion.dto.RestoreVersionResponse;
import com.docusphere.backend.documentVersion.dto.SaveChangeSummaryResponse;
import com.docusphere.backend.documentVersion.service.DocumentVersionService;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DocumentVersionController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("DocumentVersionController Unit Tests")
class DocumentVersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DocumentVersionService documentVersionService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private UUID documentId;
    private UUID versionId;
    private Long requesterId;

    @BeforeEach
    void setUp() {
        documentId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        requesterId = 99L;
    }

    @Test
    @DisplayName("Should return version history list")
    void listVersions_success() throws Exception {
        when(jwtService.extractUserId("test-token")).thenReturn(requesterId);

        DocumentVersionResponse version = DocumentVersionResponse.builder()
                .versionId(versionId)
                .versionNumber(3)
                .editedById(99L)
                .editedByName("Test User")
                .editedByRole("OWNER")
                .editedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .changeSummary("Updated title")
                .isCurrent(true)
                .isLatest(true)
                .isRestored(false)
                .isProtected(false)
                .fileSize(1024L)
                .build();

        DocumentVersionListResponse response = DocumentVersionListResponse.builder()
                .documentId(documentId)
                .currentVersionNumber(3)
                .isProtected(false)
                .versions(List.of(version))
                .build();

        when(documentVersionService.listVersions(requesterId, documentId)).thenReturn(response);

        mockMvc.perform(get("/api/documents/{documentId}/versions", documentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.data.currentVersionNumber").value(3))
                .andExpect(jsonPath("$.data.versions[0].versionId").value(versionId.toString()));
    }

    @Test
    @DisplayName("Should save change summary using trimmed summary field")
    void saveChangeSummary_success() throws Exception {
        when(jwtService.extractUserId("test-token")).thenReturn(requesterId);

        SaveChangeSummaryResponse response = SaveChangeSummaryResponse.builder()
                .documentId(documentId)
                .changeSummary("Refined summary")
                .build();
        when(documentVersionService.saveChangeSummary(requesterId, documentId, "Refined summary"))
                .thenReturn(response);

        mockMvc.perform(post("/api/documents/{documentId}/versions/summary", documentId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("summary", "  Refined summary  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.data.changeSummary").value("Refined summary"));

        verify(documentVersionService).saveChangeSummary(eq(requesterId), eq(documentId), eq("Refined summary"));
    }

    @Test
    @DisplayName("Should preview version with password header fallback")
    void previewVersion_success() throws Exception {
        when(jwtService.extractUserId("test-token")).thenReturn(requesterId);

        OnlyOfficeConfig config = OnlyOfficeConfig.builder()
                .documentType("word")
                .width("100%")
                .height("100%")
                .token("doc-token")
                .build();
        when(documentVersionService.previewVersion(requesterId, documentId, versionId, "secret123", "share-token"))
                .thenReturn(config);

        mockMvc.perform(get("/api/documents/{documentId}/versions/{versionId}/preview", documentId, versionId)
                        .header("Authorization", "Bearer test-token")
                        .header("X-Document-Password", "secret123")
                        .param("token", "share-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("word"))
                .andExpect(jsonPath("$.token").value("doc-token"));
    }

    @Test
    @DisplayName("Should restore selected version")
    void restoreVersion_success() throws Exception {
        when(jwtService.extractUserId("test-token")).thenReturn(requesterId);

        RestoreVersionResponse response = RestoreVersionResponse.builder()
                .documentId(documentId)
                .restoredFromVersionId(versionId)
                .restoredFromVersionNumber(2)
                .backupVersionId(UUID.randomUUID())
                .backupVersionNumber(3)
                .restoredAt(LocalDateTime.now())
                .build();
        when(documentVersionService.restoreVersion(requesterId, documentId, versionId))
                .thenReturn(response);

        mockMvc.perform(post("/api/documents/{documentId}/versions/{versionId}/restore", documentId, versionId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.data.restoredFromVersionId").value(versionId.toString()))
                .andExpect(jsonPath("$.data.restoredFromVersionNumber").value(2));
    }
}
