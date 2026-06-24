package com.docusphere.backend.onlyoffice.service;

import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OnlyOfficeConfigBuilderServiceTest {

    private OnlyOfficeConfigBuilderService builderService;
    private Document document;
    private User user;
    private UUID documentId;

    @BeforeEach
    void setUp() {
        builderService = new OnlyOfficeConfigBuilderService();
        ReflectionTestUtils.setField(builderService, "backendUrl", "http://host.docker.internal:8080");
        ReflectionTestUtils.setField(builderService, "onlyofficeCallbackUrl", "http://localhost:8080/api/onlyoffice/callback");
        ReflectionTestUtils.setField(builderService, "onlyofficeJwtSecret", "V8pX9iu5gDWzQrHP5Od62XOOiuOnlrtF");

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
    @DisplayName("Should build valid OnlyOfficeConfig with correct attributes")
    void testBuildConfig_Success() {
        OnlyOfficeConfig config = builderService.buildConfig(
                document, user, true, true, true, "docKey123", "callbackToken123"
        );

        assertNotNull(config);
        assertEquals("word", config.getDocumentType());
        assertEquals("100%", config.getWidth());
        assertEquals("100%", config.getHeight());
        assertNotNull(config.getToken());
        assertFalse(config.getToken().isEmpty());

        assertNotNull(config.getDocument());
        assertEquals("docx", config.getDocument().getFileType());
        assertEquals("docKey123", config.getDocument().getKey());
        assertEquals("test_doc.docx", config.getDocument().getTitle());
        assertTrue(
            config.getDocument().getUrl().startsWith(
                "http://host.docker.internal:8080/api/documents/"
                + documentId
                + "/download"
            )
        );
        assertTrue(config.getDocument().getUrl().contains("cb="));

        assertTrue(config.getDocument().getPermissions().isEdit());
        assertTrue(config.getDocument().getPermissions().isComment());
        assertTrue(config.getDocument().getPermissions().isDownload());

        assertNotNull(config.getEditorConfig());
        assertEquals("edit", config.getEditorConfig().getMode());
        assertEquals("en", config.getEditorConfig().getLang());
        assertEquals("Jane Doe", config.getEditorConfig().getUser().getName());
        assertEquals("99", config.getEditorConfig().getUser().getId());

        assertEquals("http://localhost:8080/api/onlyoffice/callback?id=" + documentId + "&token=callbackToken123",
                config.getEditorConfig().getCallbackUrl());
    }
}
