package com.docusphere.backend.onlyoffice.service;

import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlyOfficeConfigBuilderServiceTest {

    private OnlyOfficeConfigBuilderService builderService;
    private Document document;
    private User user;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        builderService = new OnlyOfficeConfigBuilderService();
        ReflectionTestUtils.setField(builderService, "frontendUrl", "http://localhost:5173");
        ReflectionTestUtils.setField(builderService, "onlyofficeCallbackUrl", "http://host.docker.internal:8080/api/onlyoffice/callback");
        ReflectionTestUtils.setField(builderService, "onlyofficeJwtSecret", "V8pX9iu5gDWzQrHP5Od62XOOiuOnlrtF");
        ReflectionTestUtils.setField(builderService, "onlyOfficeDocumentDownloadBaseUrl", "http://host.docker.internal:8080");

        documentId = UUID.randomUUID();
        document = Document.builder()
                .id(documentId)
                .name("test_doc.docx")
                .type("docx")
                .fileUrl("http://supabase.com/test_doc.docx")
                .build();

        user = new User();
        user.setId(99L);
        user.setFullName("Jane Doe");
    }

    @Test
    @DisplayName("Should build document.url using Docker-reachable backend download endpoint")
    void testBuildConfig_usesBackendDownloadUrl() {
        OnlyOfficeConfig config = builderService.buildConfig(
                document, user, true, true, true, "docKey123", "callbackToken123", "dl-token-abc", null
        );

        assertNotNull(config);
        String documentUrl = config.getDocument().getUrl();
        assertTrue(documentUrl.startsWith("http://host.docker.internal:8080/api/documents/" + documentId + "/download"));
        assertTrue(documentUrl.contains("dlToken=dl-token-abc"));
        assertFalse(documentUrl.contains("supabase.com"));
        assertEquals("docx", config.getDocument().getFileType());
    }

    @Test
    @DisplayName("Should include share token and dlToken for shared editing sessions")
    void testBuildConfig_includesShareTokenAndDlToken() {
        OnlyOfficeConfig config = builderService.buildConfig(
                document, user, true, true, true, "docKey123", "callbackToken123", "dl-token-abc", "share-token-xyz"
        );

        String documentUrl = config.getDocument().getUrl();
        assertTrue(documentUrl.contains("token=share-token-xyz"));
        assertTrue(documentUrl.contains("dlToken=dl-token-abc"));
        assertTrue(documentUrl.indexOf("token=") < documentUrl.indexOf("dlToken="));
        assertEquals(
                "http://localhost:5173/share/share-token-xyz",
                config.getEditorConfig().getCustomization().getGoback().getUrl()
        );
    }
}
