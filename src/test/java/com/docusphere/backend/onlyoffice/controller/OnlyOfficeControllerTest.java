package com.docusphere.backend.onlyoffice.controller;

import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.audit.service.AuditService;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.authentication.service.security.CustomUserDetailsService;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentShare.service.DocumentSharingService;
import com.docusphere.backend.documentVersion.service.DocumentEditSaveService;
import com.docusphere.backend.documentVersion.service.DocumentVersionPermissionService;
import com.docusphere.backend.documentVersion.service.DocumentVersionService;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeCallback;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import com.docusphere.backend.onlyoffice.service.OnlyOfficeEditorConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OnlyOfficeController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("OnlyOfficeController Unit Tests")
class OnlyOfficeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OnlyOfficeController controller;

    @MockitoBean
    private DocumentRepository documentRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private DocumentVersionPermissionService permissionService;

    @MockitoBean
    private DocumentEditSaveService editSaveService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private OnlyOfficeEditorConfigService editorConfigService;

    @MockitoBean
    private DocumentVersionService documentVersionService;

    @MockitoBean
    private DocumentSharingService documentSharingService;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private RestTemplate mockRestTemplate;

    private UUID documentId;
    private Long userId;
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        documentId = UUID.randomUUID();
        userId = 456L;
        jwtSecret = "my-test-super-secret-key-that-is-at-least-256-bits-long";

        ReflectionTestUtils.setField(controller, "secretKey", jwtSecret);
        mockRestTemplate = org.mockito.Mockito.mock(RestTemplate.class);
        ReflectionTestUtils.setField(controller, "restTemplate", mockRestTemplate);
    }

    private String createValidCallbackToken(UUID docId, Long uId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("documentId", docId.toString());
        claims.put("userId", uId);

        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();
    }

    @Test
    @DisplayName("Should return ONLYOFFICE config from editor config service")
    void testGetEditorConfig_Success() throws Exception {
        OnlyOfficeConfig expectedConfig = OnlyOfficeConfig.builder()
                .documentType("word")
                .document(OnlyOfficeConfig.DocumentInfo.builder()
                        .fileType("docx")
                        .title("report.docx")
                        .url("http://host.docker.internal:8080/api/documents/" + documentId + "/download?token=share&dlToken=dl")
                        .build())
                .editorConfig(OnlyOfficeConfig.EditorConfig.builder()
                        .mode("edit")
                        .user(OnlyOfficeConfig.UserInfo.builder()
                                .name("invitee@example.com")
                                .build())
                        .build())
                .build();

        when(editorConfigService.buildEditorConfig(eq(documentId), eq("share-token"), isNull(), any()))
                .thenReturn(expectedConfig);

        mockMvc.perform(get("/api/editor/documents/{id}", documentId)
                        .param("token", "share-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("word"))
                .andExpect(jsonPath("$.document.title").value("report.docx"))
                .andExpect(jsonPath("$.editorConfig.mode").value("edit"));
    }

    @Test
    @DisplayName("Should propagate unauthorized from editor config service")
    void testGetEditorConfig_Unauthorized() throws Exception {
        when(editorConfigService.buildEditorConfig(eq(documentId), isNull(), isNull(), any()))
                .thenThrow(new UnauthorizedAccessException("You do not have access to view this document"));

        mockMvc.perform(get("/api/editor/documents/{id}", documentId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should save document from callback with status 2")
    void testHandleCallback_SaveSuccess() throws Exception {
        String callbackToken = createValidCallbackToken(documentId, userId);

        OnlyOfficeCallback callback = new OnlyOfficeCallback();
        callback.setStatus(2);
        callback.setUrl("http://onlyoffice.test/download/doc");

        Document doc = new Document();
        doc.setId(documentId);
        doc.setOwnerId(userId);

        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(doc));
        when(permissionService.canEdit(doc, userId, null)).thenReturn(true);
        when(mockRestTemplate.getForObject("http://onlyoffice.test/download/doc", byte[].class))
                .thenReturn("updated content".getBytes());

        mockMvc.perform(post("/api/onlyoffice/callback")
                        .param("id", documentId.toString())
                        .param("token", callbackToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0));

        verify(editSaveService).processOnlyOfficeSave(eq(documentId), eq(userId), any(byte[].class), isNull(), isNull());
    }

    @Test
    @DisplayName("Should return 403 on callback with invalid token")
    void testHandleCallback_InvalidToken() throws Exception {
        OnlyOfficeCallback callback = new OnlyOfficeCallback();
        callback.setStatus(2);

        mockMvc.perform(post("/api/onlyoffice/callback")
                        .param("id", documentId.toString())
                        .param("token", "invalid-token-signature")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(1));

        verify(editSaveService, never()).processOnlyOfficeSave(any(), any(), any(), any(), any());
    }
}
