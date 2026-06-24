package com.docusphere.backend.onlyoffice.controller;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.documentVersion.service.DocumentEditSaveService;
import com.docusphere.backend.documentVersion.service.DocumentVersionService;
import com.docusphere.backend.onlyoffice.service.OnlyOfficeConfigBuilderService;
import com.docusphere.backend.onlyoffice.service.DocumentEditPermissionService;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeCallback;
import com.docusphere.backend.authentication.service.security.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
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
    private DocumentEditPermissionService permissionService;

    @MockitoBean
    private FileStorageService fileStorageService;

    @MockitoBean
    private DocumentEditSaveService editSaveService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private OnlyOfficeConfigBuilderService configBuilderService;

    @MockitoBean
    private DocumentVersionService documentVersionService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    private RestTemplate mockRestTemplate;

    private UUID documentId;
    private Long userId;
    private String authToken;
    private String jwtSecret;

    @BeforeEach
    void setUp() {
        documentId = UUID.randomUUID();
        userId = 456L;
        authToken = "Bearer valid-jwt-token";
        jwtSecret = "my-test-super-secret-key-that-is-at-least-256-bits-long";

        // Inject jwt.secret Value
        ReflectionTestUtils.setField(controller, "secretKey", jwtSecret);
        ReflectionTestUtils.setField(controller, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(controller, "onlyofficeCallbackUrl", "http://localhost:8080/api/onlyoffice/callback");
        ReflectionTestUtils.setField(controller, "supabaseUrl", "http://supabase.test");
        ReflectionTestUtils.setField(controller, "serviceKey", "test-service-key");
        ReflectionTestUtils.setField(controller, "bucketName", "documents");

        mockRestTemplate = mock(RestTemplate.class);
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

    // ==================== GET CONFIG TESTS ====================

    @Test
    @DisplayName("Should return ONLYOFFICE config successfully when user has permission")
    void testGetEditorConfig_Success() throws Exception {
        when(jwtService.extractUserId("valid-jwt-token")).thenReturn(userId);

        Document doc = new Document();
        doc.setId(documentId);
        doc.setName("report.docx");
        doc.setOwnerId(userId);
        doc.setStorageKey("folders/report.docx");
        doc.setFileUrl("http://supabase.test/folders/report.docx");
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());

        User user = new User();
        user.setId(userId);
        user.setFullName("John Doe");

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(permissionService.canView(doc, userId)).thenReturn(true);
        when(permissionService.canEdit(doc, userId)).thenReturn(true);
        when(permissionService.canDownload(doc, userId)).thenReturn(true);

        OnlyOfficeConfig expectedConfig = OnlyOfficeConfig.builder()
                .documentType("word")
                .document(OnlyOfficeConfig.DocumentInfo.builder()
                        .fileType("docx")
                        .title("report.docx")
                        .url("http://supabase.test/folders/report.docx")
                        .build())
                .editorConfig(OnlyOfficeConfig.EditorConfig.builder()
                        .mode("edit")
                        .user(OnlyOfficeConfig.UserInfo.builder()
                                .name("John Doe")
                                .build())
                        .build())
                .build();

        when(configBuilderService.buildConfig(any(Document.class), any(User.class), anyBoolean(), anyBoolean(), anyBoolean(), anyString(), anyString()))
                .thenReturn(expectedConfig);

        mockMvc.perform(get("/api/editor/documents/{id}", documentId)
                .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("word"))
                .andExpect(jsonPath("$.document.title").value("report.docx"))
                .andExpect(jsonPath("$.document.url").value("http://supabase.test/folders/report.docx"))
                .andExpect(jsonPath("$.editorConfig.mode").value("edit"))
                .andExpect(jsonPath("$.editorConfig.user.name").value("John Doe"));
    }

    @Test
    @DisplayName("Should throw 403 when user does not have permission to view document")
    void testGetEditorConfig_Unauthorized() throws Exception {
        when(jwtService.extractUserId("valid-jwt-token")).thenReturn(userId);

        Document doc = new Document();
        doc.setId(documentId);
        doc.setOwnerId(999L); // different owner

        User user = new User();
        user.setId(userId);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(permissionService.canView(doc, userId)).thenReturn(false);

        mockMvc.perform(get("/api/editor/documents/{id}", documentId)
                .header("Authorization", authToken))
                .andExpect(status().isForbidden());
    }

    // ==================== CALLBACK TESTS ====================

    @Test
    @DisplayName("Should successfully update document from callback with status 2 (save)")
    void testHandleCallback_SaveSuccess() throws Exception {
        String callbackToken = createValidCallbackToken(documentId, userId);

        OnlyOfficeCallback callback = new OnlyOfficeCallback();
        callback.setStatus(2);
        callback.setUrl("http://onlyoffice.test/download/doc");

        Document doc = new Document();
        doc.setId(documentId);
        doc.setStorageKey("folders/report.docx");
        doc.setSizeBytes(100L);

        when(documentRepository.findById(documentId)).thenReturn(Optional.of(doc));

        // Mock RestTemplate download
        byte[] fileBytes = "updated content".getBytes();
        when(mockRestTemplate.getForObject("http://onlyoffice.test/download/doc", byte[].class))
                .thenReturn(fileBytes);

        // Mock atomic PUT overwrite success
        ResponseEntity<String> successResponse = new ResponseEntity<>("OK", HttpStatus.OK);
        when(mockRestTemplate.exchange(
                eq("http://supabase.test/storage/v1/object/documents/folders/report.docx"),
                eq(HttpMethod.PUT),
                any(),
                eq(String.class)
        )).thenReturn(successResponse);

        mockMvc.perform(post("/api/onlyoffice/callback")
                .param("id", documentId.toString())
                .param("token", callbackToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0));

        verify(editSaveService, times(1)).processOnlyOfficeSave(
                eq(documentId),
                eq(userId),
                eq(fileBytes),
                eq(null)
        );
    }

    @Test
    @DisplayName("Should delegate callback save to DocumentEditSaveService")
    void testHandleCallback_SaveFallback() throws Exception {
        String callbackToken = createValidCallbackToken(documentId, userId);

        OnlyOfficeCallback callback = new OnlyOfficeCallback();
        callback.setStatus(2);
        callback.setUrl("http://onlyoffice.test/download/doc");

        byte[] fileBytes = "updated content".getBytes();
        when(mockRestTemplate.getForObject("http://onlyoffice.test/download/doc", byte[].class))
                .thenReturn(fileBytes);

        mockMvc.perform(post("/api/onlyoffice/callback")
                .param("id", documentId.toString())
                .param("token", callbackToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(callback)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error").value(0));

        verify(editSaveService, times(1)).processOnlyOfficeSave(
                eq(documentId),
                eq(userId),
                eq(fileBytes),
                eq(null)
        );
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
    }
}
