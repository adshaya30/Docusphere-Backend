package com.docusphere.backend.admin.document.controller;

import com.docusphere.backend.admin.document.dto.AdminDocumentView;
import com.docusphere.backend.admin.document.services.AdminDocumentService;
import com.docusphere.backend.authentication.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminDocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminDocumentController Unit Tests")
class AdminDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminDocumentService adminDocumentService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("Should return all documents")
    void testGetAllDocuments_Success() throws Exception {
        UUID docId = UUID.randomUUID();
        AdminDocumentView view = new AdminDocumentView();
        view.setId(docId);
        view.setName("Mock Document");

        when(adminDocumentService.getAllDocuments()).thenReturn(Collections.singletonList(view));

        mockMvc.perform(get("/api/admin/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(docId.toString()))
                .andExpect(jsonPath("$[0].name").value("Mock Document"));
    }

    @Test
    @DisplayName("Should return documents filtered by team ID")
    void testGetDocumentsByTeam_Success() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        AdminDocumentView view = new AdminDocumentView();
        view.setId(docId);
        view.setName("Team Document");
        view.setTeamId(teamId);

        when(adminDocumentService.getDocumentsByTeam(teamId)).thenReturn(Collections.singletonList(view));

        mockMvc.perform(get("/api/admin/documents").param("teamId", teamId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(docId.toString()))
                .andExpect(jsonPath("$[0].teamId").value(teamId.toString()));
    }

    @Test
    @DisplayName("Should return single document by ID")
    void testGetDocument_Success() throws Exception {
        UUID docId = UUID.randomUUID();
        AdminDocumentView view = new AdminDocumentView();
        view.setId(docId);
        view.setName("Specific Doc");

        when(adminDocumentService.getDocument(docId)).thenReturn(view);

        mockMvc.perform(get("/api/admin/documents/{documentId}", docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(docId.toString()))
                .andExpect(jsonPath("$.name").value("Specific Doc"));
    }

    @Test
    @DisplayName("Should delete a document")
    void testDeleteDocument_Success() throws Exception {
        UUID docId = UUID.randomUUID();

        doNothing().when(adminDocumentService).deleteDocument(docId);

        mockMvc.perform(delete("/api/admin/documents/{documentId}", docId))
                .andExpect(status().isNoContent());

        verify(adminDocumentService, times(1)).deleteDocument(docId);
    }
}
