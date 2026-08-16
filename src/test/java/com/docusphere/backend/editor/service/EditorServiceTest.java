package com.docusphere.backend.editor.service;

import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.editor.dto.CreateEditorDocumentRequest;
import com.docusphere.backend.editor.dto.SaveEditorDocumentRequest;
import com.docusphere.backend.editor.entity.EditorDocument;
import com.docusphere.backend.editor.repository.EditorDocumentRepository;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EditorServiceTest {

    @Mock
    private EditorDocumentRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    private EditorServiceImpl service;

    private final Long userId = 1L;
    private User mockUser;

    @BeforeEach
    void setUp() {
        service = new EditorServiceImpl(repository, userRepository, fileStorageService);
        ReflectionTestUtils.setField(service, "jwtSecret", "my-test-super-secret-key-that-is-at-least-256-bits-long-and-secure");
        ReflectionTestUtils.setField(service, "onlyofficeCallbackUrl", "http://localhost:8080/api/onlyoffice/callback");
        ReflectionTestUtils.setField(service, "onlyofficeJwtSecret", "V8pX9iu5gDWzQrHP5Od62XOOiuOnlrtF");

        mockUser = new User();
        mockUser.setId(userId);
        mockUser.setFullName("John Doe");
        mockUser.setEmail("john@example.com");
    }

    @Test
    void createDocument_shouldSuccessfullyCreateAndUpload() {
        // Arrange
        CreateEditorDocumentRequest req = new CreateEditorDocumentRequest("MyDoc", "word");
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(fileStorageService.uploadFile(any(File.class), anyString())).thenReturn("path");
        when(fileStorageService.getPublicUrl(anyString())).thenReturn("http://supabase.test/path");

        // Act
        OnlyOfficeConfig config = service.createDocument(userId, req);

        // Assert
        assertNotNull(config);
        assertEquals("word", config.getDocumentType());
        verify(repository, times(1)).save(any(EditorDocument.class));
    }

    @Test
    void listDocuments_shouldReturnUserDocuments() {
        // Arrange
        List<EditorDocument> docs = new ArrayList<>();
        docs.add(EditorDocument.builder().ownerId(userId).name("Doc1").build());
        when(repository.findByOwnerId(userId)).thenReturn(docs);

        // Act
        List<EditorDocument> result = service.listDocuments(userId);

        // Assert
        assertEquals(1, result.size());
        assertEquals("Doc1", result.get(0).getName());
    }

    @Test
    void getDocumentConfig_shouldReturnConfigForOwner() {
        // Arrange
        UUID docId = UUID.randomUUID();
        EditorDocument doc = EditorDocument.builder()
                .id(docId)
                .ownerId(userId)
                .name("Doc1")
                .type("docx")
                .storagePath("path")
                .build();
        when(repository.findById(docId)).thenReturn(Optional.of(doc));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(fileStorageService.getPublicUrl("path")).thenReturn("http://supabase.test/path");

        // Act
        OnlyOfficeConfig config = service.getDocumentConfig(userId, docId);

        // Assert
        assertNotNull(config);
        assertEquals("Doc1", config.getDocument().getTitle());
    }

    @Test
    void saveMetadata_shouldUpdateNameAndStatus() {
        // Arrange
        UUID docId = UUID.randomUUID();
        EditorDocument doc = EditorDocument.builder()
                .id(docId)
                .ownerId(userId)
                .name("OldName")
                .build();
        when(repository.findById(docId)).thenReturn(Optional.of(doc));
        when(repository.save(any(EditorDocument.class))).thenAnswer(i -> i.getArguments()[0]);

        SaveEditorDocumentRequest req = new SaveEditorDocumentRequest(docId, "NewName", "SAVED");

        // Act
        EditorDocument result = service.saveMetadata(userId, req);

        // Assert
        assertEquals("NewName", result.getName());
        assertEquals("SAVED", result.getStatus());
    }

    @Test
    void deleteDocument_shouldDeleteFromStorageAndRepo() {
        // Arrange
        UUID docId = UUID.randomUUID();
        EditorDocument doc = EditorDocument.builder()
                .id(docId)
                .ownerId(userId)
                .storagePath("path")
                .build();
        when(repository.findById(docId)).thenReturn(Optional.of(doc));

        // Act
        service.deleteDocument(userId, docId);

        // Assert
        verify(fileStorageService, times(1)).deleteFile("path");
        verify(repository, times(1)).delete(doc);
    }
}
