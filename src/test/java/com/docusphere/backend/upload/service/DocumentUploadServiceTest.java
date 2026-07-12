package com.docusphere.backend.upload.service;

import com.docusphere.backend.Common.exception.FileUploadException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.documentAction.service.TeamAccessValidator;
import com.docusphere.backend.team.repository.TeamRepository;
import com.docusphere.backend.notification.service.StorageAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DocumentUploadServiceTest {

    private DocumentRepository documentRepository;
    private FileStorageService fileStorageService;
    private TeamAccessValidator teamAccessValidator;
    private TeamRepository teamRepository;
    private StorageAlertService storageAlertService;
    private DocumentUploadService documentUploadService;

    @TempDir
    Path tempRoot;

    @BeforeEach
    void setUp() throws Exception {
        documentRepository = mock(DocumentRepository.class);
        fileStorageService = mock(FileStorageService.class);
        teamAccessValidator = mock(TeamAccessValidator.class);
        teamRepository = mock(TeamRepository.class);
        storageAlertService = mock(StorageAlertService.class);
        documentUploadService = new DocumentUploadService(
                documentRepository,
                fileStorageService,
                teamAccessValidator,
                teamRepository,
                storageAlertService,
                tempRoot.toString()
        );
    }

    @Test
    void generateFileId_shouldReturnDifferentValues() {
        String first = documentUploadService.generateFileId();
        String second = documentUploadService.generateFileId();

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
    }

    @Test
    void uploadChunk_shouldReturnInProgressWhenNotAllChunksPresent() {
        MockMultipartFile chunk = chunk("demo.pdf", "part-1");

        DocumentUploadService.UploadResult result = documentUploadService.uploadChunk(
                chunk, "demo.pdf", "file-in-progress", 0, 2, 101L, null
        );

        assertFalse(result.isCompleted());
        assertNull(result.getDocumentId());
        verifyNoInteractions(fileStorageService);
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void uploadChunk_shouldFinalizeAndPersistDocumentWhenLastChunkArrives() {
        String fileId = "file-complete";
        UUID expectedId = UUID.randomUUID();

        when(documentRepository.findByFileId(fileId)).thenReturn(Optional.empty());
        when(fileStorageService.uploadFile(any(java.io.File.class), any(String.class)))
                .thenReturn("https://example.supabase.co/storage/v1/object/public/documents/file-complete_team_file.pdf");
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document doc = invocation.getArgument(0);
            doc.setId(expectedId);
            return doc;
        });

        DocumentUploadService.UploadResult firstResult = documentUploadService.uploadChunk(
                chunk("team file.pdf", "Hello "), "team file.pdf", fileId, 0, 2, 77L, null
        );
        DocumentUploadService.UploadResult finalResult = documentUploadService.uploadChunk(
                chunk("team file.pdf", "World"), "team file.pdf", fileId, 1, 2, 77L, null
        );

        assertFalse(firstResult.isCompleted());
        assertTrue(finalResult.isCompleted());
        assertEquals(expectedId.toString(), finalResult.getDocumentId());

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(documentCaptor.capture());
        Document saved = documentCaptor.getValue();

        assertAll(
                () -> assertEquals(fileId, saved.getFileId()),
                () -> assertEquals("team_file.pdf", saved.getName()),
                () -> assertEquals("pdf", saved.getType()),
                () -> assertEquals(77L, saved.getOwnerId()),
                () -> assertEquals(Document.UploadStatus.COMPLETED, saved.getStatus()),
                () -> assertNotNull(saved.getStorageKey()),
                () -> assertNotNull(saved.getFileUrl())
        );
    }

    @Test
    void uploadChunk_shouldThrowForInvalidChunkIndex() {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class, () ->
                documentUploadService.uploadChunk(chunk("demo.pdf", "x"), "demo.pdf", "file-id", 2, 2, 10L, null)
        );

        assertEquals("Invalid chunk index", exception.getMessage());
    }

    @Test
    void uploadChunk_shouldThrowForInvalidTotalChunks() {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class, () ->
                documentUploadService.uploadChunk(chunk("demo.pdf", "x"), "demo.pdf", "file-id", 0, 0, 10L, null)
        );

        assertEquals("Invalid chunk index", exception.getMessage());
    }

    @Test
    void uploadChunk_shouldThrowForMissingFileName() {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class, () ->
                documentUploadService.uploadChunk(chunk("demo.pdf", "x"), "   ", "file-id", 0, 1, 10L, null)
        );

        assertEquals("fileName required", exception.getMessage());
    }

    @Test
    void uploadChunk_shouldThrowForMissingFileId() {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class, () ->
                documentUploadService.uploadChunk(chunk("demo.pdf", "x"), "demo.pdf", " ", 0, 1, 10L, null)
        );

        assertEquals("fileId required", exception.getMessage());
    }

    @Test
    void uploadChunk_shouldThrowForMissingOwner() {
        InvalidRequestException exception = assertThrows(InvalidRequestException.class, () ->
                documentUploadService.uploadChunk(chunk("demo.pdf", "x"), "demo.pdf", "file-id", 0, 1, null, null)
        );

        assertEquals("Invalid user", exception.getMessage());
    }

    @Test
    void uploadChunk_shouldThrowForUnsupportedExtension() {
        FileUploadException exception = assertThrows(FileUploadException.class, () ->
                documentUploadService.uploadChunk(chunk("virus.exe", "x"), "virus.exe", "file-id", 0, 1, 10L, null)
        );

        assertTrue(exception.getMessage().contains("Unsupported file type: exe"));
    }

    @Test
    void uploadChunk_shouldThrowForInvalidFileNameWithoutExtension() {
        FileUploadException exception = assertThrows(FileUploadException.class, () ->
                documentUploadService.uploadChunk(chunk("noextension", "x"), "noextension", "file-id", 0, 1, 10L, null)
        );

        assertEquals("Invalid file name", exception.getMessage());
    }

    @Test
    void uploadChunk_shouldThrowForDuplicateFileId() {
        String fileId = "duplicate-file";
        when(documentRepository.findByFileId(fileId)).thenReturn(Optional.of(new Document()));

        InvalidRequestException exception = assertThrows(InvalidRequestException.class, () ->
                documentUploadService.uploadChunk(chunk("demo.pdf", "x"), "demo.pdf", fileId, 0, 1, 10L, null)
        );

        assertEquals("Duplicate upload", exception.getMessage());
        verify(fileStorageService, never()).uploadFile(any(), any());
    }

    @Test
    void uploadChunk_shouldWrapUnexpectedStorageFailure() {
        String fileId = "broken-storage";
        when(documentRepository.findByFileId(fileId)).thenReturn(Optional.empty());
        when(fileStorageService.uploadFile(any(java.io.File.class), any(String.class)))
                .thenThrow(new RuntimeException("storage unavailable"));

        FileUploadException exception = assertThrows(FileUploadException.class, () -> {
            documentUploadService.uploadChunk(chunk("demo.pdf", "Hello "), "demo.pdf", fileId, 0, 2, 10L, null);
            documentUploadService.uploadChunk(chunk("demo.pdf", "World"), "demo.pdf", fileId, 1, 2, 10L, null);
        });

        assertTrue(exception.getMessage().contains("Upload failed: storage unavailable"));
        verify(documentRepository, never()).save(any(Document.class));
    }

    private MockMultipartFile chunk(String fileName, String content) {
        return new MockMultipartFile("file", fileName, "application/octet-stream", content.getBytes());
    }
}