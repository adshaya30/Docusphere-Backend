package com.docusphere.backend.editor.controller;

import com.docusphere.backend.Common.exception.GlobalExceptionHandler;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.authentication.service.security.CustomUserDetailsService;
import com.docusphere.backend.editor.dto.CreateEditorDocumentRequest;
import com.docusphere.backend.editor.dto.SaveEditorDocumentRequest;
import com.docusphere.backend.editor.entity.EditorDocument;
import com.docusphere.backend.editor.service.EditorService;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EditorController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EditorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EditorService editorService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private final Long userId = 1L;
    private final String token = "valid-token";

    @BeforeEach
    void setUp() {
        when(jwtService.extractUserId(anyString())).thenReturn(userId);
    }

    @Test
    void createDocument_shouldReturnConfig() throws Exception {
        CreateEditorDocumentRequest req = new CreateEditorDocumentRequest("MyDoc", "word");
        OnlyOfficeConfig config = OnlyOfficeConfig.builder().documentType("word").build();
        when(editorService.createDocument(eq(userId), any(CreateEditorDocumentRequest.class))).thenReturn(config);

        mockMvc.perform(post("/api/editor/create")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.documentType").value("word"));
    }

    @Test
    void listDocuments_shouldReturnList() throws Exception {
        List<EditorDocument> docs = new ArrayList<>();
        docs.add(EditorDocument.builder().ownerId(userId).name("Doc1").build());
        when(editorService.listDocuments(userId)).thenReturn(docs);

        mockMvc.perform(get("/api/editor/list")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data[0].name").value("Doc1"));
    }

    @Test
    void getDocument_shouldReturnConfig() throws Exception {
        UUID docId = UUID.randomUUID();
        OnlyOfficeConfig config = OnlyOfficeConfig.builder().documentType("word").build();
        when(editorService.getDocumentConfig(userId, docId)).thenReturn(config);

        mockMvc.perform(get("/api/editor/" + docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void saveMetadata_shouldReturnUpdatedDoc() throws Exception {
        UUID docId = UUID.randomUUID();
        SaveEditorDocumentRequest req = new SaveEditorDocumentRequest(docId, "NewName", "SAVED");
        EditorDocument doc = EditorDocument.builder().id(docId).name("NewName").status("SAVED").build();
        when(editorService.saveMetadata(eq(userId), any(SaveEditorDocumentRequest.class))).thenReturn(doc);

        mockMvc.perform(put("/api/editor/save")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.name").value("NewName"));
    }

    @Test
    void deleteDocument_shouldReturnSuccess() throws Exception {
        UUID docId = UUID.randomUUID();
        doNothing().when(editorService).deleteDocument(userId, docId);

        mockMvc.perform(delete("/api/editor/" + docId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));
    }
}
