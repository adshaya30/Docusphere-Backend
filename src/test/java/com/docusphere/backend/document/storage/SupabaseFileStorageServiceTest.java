package com.docusphere.backend.document.storage;

import com.docusphere.backend.Common.exception.FileUploadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SupabaseFileStorageServiceTest {

    private SupabaseFileStorageService storageService;
    private RestTemplate restTemplate;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storageService = new SupabaseFileStorageService();
        restTemplate = mock(RestTemplate.class);

        ReflectionTestUtils.setField(storageService, "supabaseUrl", "https://supabase.example.com");
        ReflectionTestUtils.setField(storageService, "serviceKey", "service-key-123");
        ReflectionTestUtils.setField(storageService, "bucketName", "documents");
        ReflectionTestUtils.setField(storageService, "restTemplate", restTemplate);
    }

    @Test
    void uploadFile_shouldPostFileAndReturnPublicUrl() throws Exception {
        Path source = Files.createTempFile(tempDir, "report", ".pdf");
        Files.writeString(source, "hello storage", StandardCharsets.UTF_8);

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("uploaded"));

        String publicUrl = storageService.uploadFile(source.toFile(), "folder/report.pdf");

        assertEquals("https://supabase.example.com/storage/v1/object/public/documents/folder/report.pdf", publicUrl);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.POST), requestCaptor.capture(), eq(String.class));

        assertEquals("https://supabase.example.com/storage/v1/object/documents/folder/report.pdf", urlCaptor.getValue());

        HttpHeaders headers = requestCaptor.getValue().getHeaders();
        assertEquals("Bearer service-key-123", headers.getFirst(HttpHeaders.AUTHORIZATION));
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, headers.getContentType());

        @SuppressWarnings("unchecked")
        Resource body = (Resource) requestCaptor.getValue().getBody();
        assertNotNull(body);
    }

    @Test
    void uploadFile_shouldThrowWhenSupabaseReturnsError() throws Exception {
        Path source = Files.createTempFile(tempDir, "report", ".pdf");
        Files.writeString(source, "hello storage", StandardCharsets.UTF_8);

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.BAD_REQUEST).body("bad request"));

        FileUploadException exception = assertThrows(FileUploadException.class, () ->
                storageService.uploadFile(source.toFile(), "folder/report.pdf")
        );

        assertTrue(exception.getMessage().contains("Upload failed"));
    }
}

