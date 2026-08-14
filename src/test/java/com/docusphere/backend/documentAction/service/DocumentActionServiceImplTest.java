package com.docusphere.backend.documentAction.service;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.documentAction.dto.DocumentActionResponse;
import com.docusphere.backend.documentAction.dto.TrashDocumentsPageResponse;
import com.docusphere.backend.documentShare.service.DocumentSharingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentActionService Unit Tests")
class DocumentActionServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private TeamAccessValidator teamAccessValidator;

    @Mock
    private DocumentSharingService documentSharingService;

    @Captor
    private ArgumentCaptor<Document> documentCaptor;

    private DocumentActionServiceImpl documentActionService;

    private UUID documentId;
    private Long ownerId;
    private UUID teamId;
    private Document mockDocument;

    @BeforeEach
    void setUp() {
        documentActionService = new DocumentActionServiceImpl(
                documentRepository,
                fileStorageService,
                teamAccessValidator,
                "https://supabase.example.com",
                "documents"
        );
        ReflectionTestUtils.setField(documentActionService, "documentSharingService", documentSharingService);

        documentId = UUID.randomUUID();
        ownerId = 123L;
        teamId = UUID.randomUUID();

        mockDocument = Document.builder()
                .id(documentId)
                .fileId("file123")
                .name("Test Document.pdf")
                .type("pdf")
                .sizeBytes(1024L)
                .ownerId(ownerId)
                .teamId(teamId)
                .storageKey("storage/path/file123_test.pdf")
                .fileUrl("http://example.com/file")
                .status(Document.UploadStatus.COMPLETED)
                .deleted(false)
                .build();
    }

    // ==================== RENAME TESTS ====================

    @Test
    @DisplayName("Should successfully rename document")
    void testRename_Success() {
        // Arrange
        String newName = "Renamed Document.pdf";

        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(documentRepository.save(any())).thenReturn(mockDocument);

        // Act
        DocumentActionResponse response = documentActionService.rename(ownerId, documentId, newName);

        // Assert
        assertNotNull(response);
        verify(documentRepository, times(1)).findByIdAndDeletedFalse(documentId);
        verify(documentRepository, times(1)).save(documentCaptor.capture());
    }

    @Test
    @DisplayName("Should throw exception when renaming with null name")
    void testRename_NullName_ThrowsException() {
        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentActionService.rename(ownerId, documentId, null)
        );

        assertEquals("newName is required", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when renaming with blank name")
    void testRename_BlankName_ThrowsException() {
        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentActionService.rename(ownerId, documentId, "   ")
        );

        assertEquals("newName is required", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when document not found for rename")
    void testRename_DocumentNotFound_ThrowsException() {
        // Arrange
        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.empty());

        // Act & Assert
        DocumentNotFoundException exception = assertThrows(
                DocumentNotFoundException.class,
                () -> documentActionService.rename(ownerId, documentId, "New Name")
        );

        assertEquals("Document not found", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when non-owner attempts to rename")
    void testRename_NotOwner_ThrowsException() {
        // Arrange
        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));

        // Act & Assert
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> documentActionService.rename(999L, documentId, "New Name")
        );

        assertEquals("Only the owner can perform this action", exception.getMessage());
    }

    // ==================== MOVE TESTS ====================

    @Test
    @DisplayName("Should successfully move document to team")
    void testMove_ToTeam_Success() {
        // Arrange
        UUID newTeamId = UUID.randomUUID();

        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(teamAccessValidator.isMember(ownerId, newTeamId)).thenReturn(true);
        when(documentRepository.save(any())).thenReturn(mockDocument);

        // Act
        DocumentActionResponse response = documentActionService.move(ownerId, documentId, newTeamId);

        // Assert
        assertNotNull(response);
        verify(documentRepository, times(1)).findByIdAndDeletedFalse(documentId);
        verify(teamAccessValidator, times(1)).isMember(ownerId, newTeamId);
        verify(documentRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should successfully move document to user space")
    void testMove_ToUserSpace_Success() {
        // Arrange
        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(documentRepository.save(any())).thenReturn(mockDocument);

        // Act
        DocumentActionResponse response = documentActionService.move(ownerId, documentId, null);

        // Assert
        assertNotNull(response);
        verify(teamAccessValidator, never()).isMember(anyLong(), any());
    }

    @Test
    @DisplayName("Should throw exception when moving to same team")
    void testMove_SameTeam_ThrowsException() {
        // Arrange
        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentActionService.move(ownerId, documentId, teamId)
        );

        assertTrue(exception.getMessage().contains("already in target space"));
    }

    @Test
    @DisplayName("Should throw exception when user not member of target team")
    void testMove_NotMemberOfTeam_ThrowsException() {
        // Arrange
        UUID newTeamId = UUID.randomUUID();

        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(teamAccessValidator.isMember(ownerId, newTeamId)).thenReturn(false);

        // Act & Assert
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> documentActionService.move(ownerId, documentId, newTeamId)
        );

        assertEquals("User is not a member of the target team", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when non-owner attempts to move")
    void testMove_NotOwner_ThrowsException() {
        // Arrange
        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));

        // Act & Assert
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> documentActionService.move(999L, documentId, null)
        );

        assertEquals("Only the owner can perform this action", exception.getMessage());
    }

    // ==================== DUPLICATE TESTS ====================

    @Test
    @DisplayName("Should successfully duplicate document")
    void testDuplicate_Success() {
        // Arrange
        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(documentRepository.existsByOwnerIdAndTeamIdAndNameAndDeletedFalse(ownerId, teamId, "Test Document (1).pdf"))
                .thenReturn(false);
        when(fileStorageService.copyFile(anyString(), anyString()))
                .thenReturn("new/storage/path");
        when(documentRepository.save(any())).thenReturn(mockDocument);

        // Act
        DocumentActionResponse response = documentActionService.duplicate(ownerId, documentId);

        // Assert
        assertNotNull(response);
        verify(fileStorageService, times(1)).copyFile(anyString(), anyString());
        verify(documentRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Should generate unique duplicate names")
    void testDuplicate_UniqueName_Success() {
        // Arrange
        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(documentRepository.existsByOwnerIdAndTeamIdAndNameAndDeletedFalse(ownerId, teamId, "Test Document (1).pdf"))
                .thenReturn(true);
        when(documentRepository.existsByOwnerIdAndTeamIdAndNameAndDeletedFalse(ownerId, teamId, "Test Document (2).pdf"))
                .thenReturn(false);
        when(fileStorageService.copyFile(anyString(), anyString()))
                .thenReturn("new/storage/path");
        when(documentRepository.save(any())).thenReturn(mockDocument);

        // Act
        DocumentActionResponse response = documentActionService.duplicate(ownerId, documentId);

        // Assert
        assertNotNull(response);
        verify(fileStorageService, times(1)).copyFile(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when user cannot access document for duplication")
    void testDuplicate_NoAccess_ThrowsException() {
        // Arrange
        Long otherUserId = 999L;

        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));

        // Act & Assert
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> documentActionService.duplicate(otherUserId, documentId)
        );

        assertEquals("Only the owner can perform this action", exception.getMessage());
    }

    // ==================== MOVE TO TRASH TESTS ====================

    @Test
    @DisplayName("Should successfully move document to trash")
    void testMoveToTrash_Success() {
        // Arrange
        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(documentRepository.save(any())).thenReturn(mockDocument);

        // Act
        DocumentActionResponse response = documentActionService.moveToTrash(ownerId, documentId);

        // Assert
        assertNotNull(response);
        assertTrue(response.isDeleted());
        assertNotNull(response.getDeletedAt());

        verify(documentRepository, times(1)).findByIdAndDeletedFalse(documentId);
        verify(documentRepository, times(1)).save(documentCaptor.capture());
        
        Document savedDocument = documentCaptor.getValue();
        assertTrue(savedDocument.isDeleted());
        assertNotNull(savedDocument.getDeletedAt());
    }

    @Test
    @DisplayName("Should throw exception when non-owner attempts to move to trash")
    void testMoveToTrash_NotOwner_ThrowsException() {
        // Arrange
        when(documentRepository.findByIdAndDeletedFalse(documentId))
                .thenReturn(Optional.of(mockDocument));

        // Act & Assert
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> documentActionService.moveToTrash(999L, documentId)
        );

        assertEquals("Only the owner can perform this action", exception.getMessage());
    }

    // ==================== RESTORE FROM TRASH TESTS ====================

    @Test
    @DisplayName("Should successfully restore document from trash")
    void testRestoreFromTrash_Success() {
        // Arrange
        mockDocument.setDeleted(true);
        mockDocument.setDeletedAt(LocalDateTime.now().minusDays(1));

        when(documentRepository.findById(documentId))
                .thenReturn(Optional.of(mockDocument));
        when(documentRepository.save(any())).thenReturn(mockDocument);

        // Act
        DocumentActionResponse response = documentActionService.restoreFromTrash(ownerId, documentId);

        // Assert
        assertNotNull(response);
        assertFalse(response.isDeleted());
        assertNull(response.getDeletedAt());

        verify(documentRepository, times(1)).save(documentCaptor.capture());
        
        Document savedDocument = documentCaptor.getValue();
        assertFalse(savedDocument.isDeleted());
        assertNull(savedDocument.getDeletedAt());
    }

    @Test
    @DisplayName("Should throw exception when document not in trash")
    void testRestoreFromTrash_NotDeleted_ThrowsException() {
        // Arrange
        mockDocument.setDeleted(false);

        when(documentRepository.findById(documentId))
                .thenReturn(Optional.of(mockDocument));

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentActionService.restoreFromTrash(ownerId, documentId)
        );

        assertEquals("Document is not in trash", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when non-owner attempts to restore")
    void testRestoreFromTrash_NotOwner_ThrowsException() {
        // Arrange
        mockDocument.setDeleted(true);

        when(documentRepository.findById(documentId))
                .thenReturn(Optional.of(mockDocument));

        // Act & Assert
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> documentActionService.restoreFromTrash(999L, documentId)
        );

        assertEquals("Only the owner can perform this action", exception.getMessage());
    }

    // ==================== PERMANENT DELETE TESTS ====================

    @Test
    @DisplayName("Should successfully permanently delete document")
    void testPermanentlyDelete_Success() {
        // Arrange
        mockDocument.setDeleted(true);
        mockDocument.setDeletedAt(LocalDateTime.now().minusDays(1));

        when(documentRepository.findById(documentId))
                .thenReturn(Optional.of(mockDocument));
        doNothing().when(fileStorageService).deleteFile("storage/path/file123_test.pdf");
        doNothing().when(documentRepository).delete(mockDocument);

        // Act
        documentActionService.permanentlyDelete(ownerId, documentId);

        // Assert
        verify(fileStorageService, times(1)).deleteFile("storage/path/file123_test.pdf");
        verify(documentRepository, times(1)).delete(mockDocument);
    }

    @Test
    @DisplayName("Should throw exception when deleting non-trashed document")
    void testPermanentlyDelete_NotInTrash_ThrowsException() {
        // Arrange
        mockDocument.setDeleted(false);

        when(documentRepository.findById(documentId))
                .thenReturn(Optional.of(mockDocument));

        // Act & Assert
        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> documentActionService.permanentlyDelete(ownerId, documentId)
        );

        assertEquals("Only trashed documents can be permanently deleted", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when non-owner attempts permanent delete")
    void testPermanentlyDelete_NotOwner_ThrowsException() {
        // Arrange
        mockDocument.setDeleted(true);

        when(documentRepository.findById(documentId))
                .thenReturn(Optional.of(mockDocument));

        // Act & Assert
        UnauthorizedAccessException exception = assertThrows(
                UnauthorizedAccessException.class,
                () -> documentActionService.permanentlyDelete(999L, documentId)
        );

        assertEquals("Only the owner can perform this action", exception.getMessage());
    }

    // ==================== GET TRASH TESTS ====================

    @Test
    @DisplayName("Should successfully retrieve trash documents")
    void testGetTrash_Success() {
        // Arrange
        mockDocument.setDeleted(true);
        mockDocument.setDeletedAt(LocalDateTime.now().minusDays(1));

        Page<Document> trashPage = new PageImpl<>(
                Collections.singletonList(mockDocument),
                org.springframework.data.domain.PageRequest.of(0, 15),
                1
        );

        when(documentRepository.findByOwnerIdAndDeletedTrue(ownerId, org.springframework.data.domain.PageRequest.of(0, 15, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "deletedAt"))))
                .thenReturn(trashPage);

        // Act
        TrashDocumentsPageResponse response = documentActionService.getTrash(ownerId, 0, 15);

        // Assert
        assertNotNull(response);
        assertEquals(0, response.getPage());
        assertEquals(15, response.getSize());
        assertEquals(1, response.getTotalElements());
        assertFalse(response.isEmpty());
    }

    @Test
    @DisplayName("Should return empty trash when no documents")
    void testGetTrash_Empty_Success() {
        // Arrange
        Page<Document> emptyPage = new PageImpl<>(
                Collections.emptyList(),
                org.springframework.data.domain.PageRequest.of(0, 15),
                0
        );

        when(documentRepository.findByOwnerIdAndDeletedTrue(eq(ownerId), any(Pageable.class)))
                .thenReturn(emptyPage);

        // Act
        TrashDocumentsPageResponse response = documentActionService.getTrash(ownerId, 0, 15);

        // Assert
        assertTrue(response.isEmpty());
        assertEquals(0, response.getItems().size());
    }

    // ==================== DOWNLOAD TESTS ====================

    @Test
    @DisplayName("Should successfully download document with share token")
    void testDownload_Success() {
        // Arrange
        String shareToken = "valid-share-token";
        byte[] fileContent = "test file content".getBytes();

        when(documentSharingService.checkReadAccessByShareToken(documentId, shareToken))
                .thenReturn(mockDocument);
        when(fileStorageService.loadFile("storage/path/file123_test.pdf"))
                .thenReturn(fileContent);

        // Act
        var resource = documentActionService.downloadByShareToken(documentId, shareToken, null);

        // Assert
        assertNotNull(resource);
        verify(documentSharingService, times(1)).checkReadAccessByShareToken(documentId, shareToken);
        verify(fileStorageService, times(1)).loadFile("storage/path/file123_test.pdf");
    }

    @Test
    @DisplayName("Should resolve download filename correctly")
    void testResolveDownloadFilename_Success() {
        // Arrange
        String shareToken = "valid-share-token";

        when(documentSharingService.checkReadAccessByShareToken(documentId, shareToken))
                .thenReturn(mockDocument);

        // Act
        String filename = documentActionService.resolveDownloadFilenameByShareToken(documentId, shareToken, null);

        // Assert
        assertEquals("Test Document.pdf", filename);
        verify(documentSharingService, times(1)).checkReadAccessByShareToken(documentId, shareToken);
    }
}