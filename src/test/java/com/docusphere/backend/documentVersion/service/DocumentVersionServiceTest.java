package com.docusphere.backend.documentVersion.service;

import com.docusphere.backend.Common.exception.DocumentNotFoundException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.audit.service.AuditService;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.documentProtection.service.DocumentPasswordProtectionService;
import com.docusphere.backend.documentShare.service.DocumentSharingService;
import com.docusphere.backend.documentVersion.dto.DocumentVersionListResponse;
import com.docusphere.backend.documentVersion.dto.DocumentVersionResponse;
import com.docusphere.backend.documentVersion.dto.RestoreVersionResponse;
import com.docusphere.backend.documentVersion.dto.SaveChangeSummaryResponse;
import com.docusphere.backend.documentVersion.entity.DocumentVersion;
import com.docusphere.backend.documentVersion.repository.DocumentVersionRepository;
import com.docusphere.backend.onlyoffice.dto.OnlyOfficeConfig;
import com.docusphere.backend.onlyoffice.service.OnlyOfficeConfigBuilderService;
import com.docusphere.backend.onlyoffice.service.OnlyOfficeDownloadTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentVersionService Unit Tests")
class DocumentVersionServiceTest {

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private DocumentVersionPermissionService permissionService;

    @Mock
    private DocumentPasswordProtectionService documentPasswordProtectionService;

    @Mock
    private DocumentSharingService documentSharingService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OnlyOfficeConfigBuilderService onlyOfficeConfigBuilderService;

    @Mock
    private OnlyOfficeDownloadTokenService onlyOfficeDownloadTokenService;

    @Mock
    private DocumentVersionSummaryStore summaryStore;

    @Mock
    private DocumentEditSaveService editSaveService;

    @Mock
    private AuditService auditService;

    @Captor
    private ArgumentCaptor<DocumentVersion> versionCaptor;

    private DocumentVersionService documentVersionService;

    private UUID documentId;
    private UUID versionId;
    private Long requesterId;
    private Document document;
    private User requester;

    @BeforeEach
    void setUp() {
        documentVersionService = new DocumentVersionService(
                documentVersionRepository,
                documentRepository,
                fileStorageService,
                permissionService,
                documentPasswordProtectionService,
                documentSharingService,
                userRepository,
                onlyOfficeConfigBuilderService,
                onlyOfficeDownloadTokenService,
                summaryStore,
                editSaveService,
                auditService
        );

        documentId = UUID.randomUUID();
        versionId = UUID.randomUUID();
        requesterId = 10L;

        document = Document.builder()
                .id(documentId)
                .fileId("file-1")
                .name("Report.docx")
                .type("docx")
                .sizeBytes(2048L)
                .ownerId(requesterId)
                .storageKey("docs/report.docx")
                .fileUrl("https://example.com/report.docx")
                .deleted(false)
                .secured(false)
                .build();

        requester = new User();
        requester.setId(requesterId);
        requester.setFullName("Test Owner");
    }

    @Test
    @DisplayName("Should store normalized change summary for editor")
    void saveChangeSummary_success() {
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(permissionService.canEdit(document, requesterId)).thenReturn(true);
        when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(documentId))
                .thenReturn(Optional.empty());

        SaveChangeSummaryResponse response = documentVersionService.saveChangeSummary(
                requesterId,
                documentId,
                "  Updated executive summary  "
        );

        assertEquals(documentId, response.getDocumentId());
        assertEquals("Updated executive summary", response.getChangeSummary());
        verify(summaryStore).store(documentId, requesterId, "Updated executive summary");
    }

    @Test
    @DisplayName("Should default blank change summary to Document edited")
    void saveChangeSummary_blankSummaryUsesDefault() {
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(permissionService.canEdit(document, requesterId)).thenReturn(true);

        SaveChangeSummaryResponse response = documentVersionService.saveChangeSummary(requesterId, documentId, "   ");

        assertEquals("Document edited", response.getChangeSummary());
        verify(summaryStore, never()).store(any(), any(), any());
    }

    @Test
    @DisplayName("Should attach summary to recently created version from same editor")
    void saveChangeSummary_updatesRecentVersion() {
        DocumentVersion recentVersion = DocumentVersion.builder()
                .id(versionId)
                .documentId(documentId)
                .versionNumber(3)
                .storageKey("Versions/v3")
                .fileUrl("https://example.com/v3")
                .sizeBytes(1024L)
                .editedBy(requesterId)
                .editedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(permissionService.canEdit(document, requesterId)).thenReturn(true);
        when(documentVersionRepository.findFirstByDocumentIdOrderByVersionNumberDesc(documentId))
                .thenReturn(Optional.of(recentVersion));

        documentVersionService.saveChangeSummary(requesterId, documentId, "Post-save summary");

        verify(documentVersionRepository).save(versionCaptor.capture());
        assertEquals("Post-save summary", versionCaptor.getValue().getChangeSummary());
    }

    @Test
    @DisplayName("Should reject change summary when user cannot edit")
    void saveChangeSummary_unauthorized() {
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(permissionService.canEdit(document, requesterId)).thenReturn(false);

        assertThrows(
                UnauthorizedAccessException.class,
                () -> documentVersionService.saveChangeSummary(requesterId, documentId, "Summary")
        );
        verify(summaryStore, never()).store(any(), any(), any());
    }

    @Test
    @DisplayName("Should list versions with latest and current flags")
    void listVersions_success() {
        DocumentVersion v2 = buildVersion(UUID.randomUUID(), 2, false, requesterId);
        DocumentVersion v3 = buildVersion(versionId, 3, true, requesterId);

        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(permissionService.canView(document, requesterId, null)).thenReturn(true);
        when(documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId))
                .thenReturn(List.of(v3, v2));
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(permissionService.resolveEditorRole(document, requesterId)).thenReturn("OWNER");

        DocumentVersionListResponse response = documentVersionService.listVersions(requesterId, documentId);

        assertEquals(documentId, response.getDocumentId());
        assertEquals(3, response.getCurrentVersionNumber());
        assertFalse(response.isProtected());
        assertEquals(2, response.getVersions().size());

        DocumentVersionResponse latest = response.getVersions().get(0);
        DocumentVersionResponse older = response.getVersions().get(1);

        assertTrue(latest.isLatest());
        assertTrue(latest.isCurrent());
        assertEquals("Test Owner", latest.getEditedByName());

        assertFalse(older.isLatest());
        assertFalse(older.isCurrent());
    }

    @Test
    @DisplayName("Should attribute share-invitation edits without implying verified identity")
    void listVersions_shareInvitationAttribution() {
        DocumentVersion sharedEdit = DocumentVersion.builder()
                .id(versionId)
                .documentId(documentId)
                .versionNumber(2)
                .storageKey("Versions/v2")
                .fileUrl("https://example.com/v2")
                .sizeBytes(1024L)
                .editedBy(requesterId)
                .editedByEmail("john@example.com")
                .editedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .currentVersionSnapshot(true)
                .build();

        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(permissionService.canView(document, requesterId, null)).thenReturn(true);
        when(documentVersionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId))
                .thenReturn(List.of(sharedEdit));
        when(permissionService.resolveEditorRole(document, requesterId)).thenReturn("OWNER");

        DocumentVersionListResponse response = documentVersionService.listVersions(requesterId, documentId);

        DocumentVersionResponse version = response.getVersions().get(0);
        assertEquals("Edited via shared invitation: john@example.com", version.getEditedByName());
        assertEquals("john@example.com", version.getEditedByEmail());
        assertTrue(version.isEditedViaShareInvitation());
    }

    @Test
    @DisplayName("Should reject version list when user cannot view")
    void listVersions_unauthorized() {
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(permissionService.canView(document, requesterId, null)).thenReturn(false);

        assertThrows(
                UnauthorizedAccessException.class,
                () -> documentVersionService.listVersions(requesterId, documentId)
        );
    }

    @Test
    @DisplayName("Should return version metadata for authorized viewer")
    void getVersion_success() {
        DocumentVersion version = buildVersion(versionId, 2, false, requesterId);

        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(permissionService.canView(document, requesterId, null)).thenReturn(true);
        when(documentVersionRepository.findByIdAndDocumentId(versionId, documentId)).thenReturn(Optional.of(version));
        when(documentVersionRepository.findMaxVersionNumberByDocumentId(documentId)).thenReturn(Optional.of(2));
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(permissionService.resolveEditorRole(document, requesterId)).thenReturn("OWNER");

        DocumentVersionResponse response = documentVersionService.getVersion(requesterId, documentId, versionId);

        assertEquals(versionId, response.getVersionId());
        assertEquals(2, response.getVersionNumber());
        assertTrue(response.isLatest());
    }

    @Test
    @DisplayName("Should throw when version does not exist")
    void getVersion_notFound() {
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(permissionService.canView(document, requesterId, null)).thenReturn(true);
        when(documentVersionRepository.findByIdAndDocumentId(versionId, documentId)).thenReturn(Optional.empty());

        assertThrows(
                DocumentNotFoundException.class,
                () -> documentVersionService.getVersion(requesterId, documentId, versionId)
        );
    }

    @Test
    @DisplayName("Should build preview config and record audit")
    void previewVersion_success() {
        DocumentVersion version = buildVersion(versionId, 2, false, requesterId);
        OnlyOfficeConfig config = OnlyOfficeConfig.builder()
                .documentType("word")
                .width("100%")
                .height("100%")
                .token("preview-token")
                .build();

        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(permissionService.canView(document, requesterId, null)).thenReturn(true);
        doNothing().when(documentPasswordProtectionService)
                .requirePasswordForContentAccess(document, null, requesterId, null);
        when(documentVersionRepository.findByIdAndDocumentId(versionId, documentId)).thenReturn(Optional.of(version));
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(onlyOfficeDownloadTokenService.createVersionDownloadToken(documentId, versionId, requesterId, null))
                .thenReturn("dl-token");
        when(onlyOfficeConfigBuilderService.buildVersionPreviewConfig(
                eq(document),
                eq(versionId),
                eq("v2 - Report.docx"),
                any(String.class),
                eq(requester),
                eq("dl-token"),
                isNull()
        )).thenReturn(config);

        OnlyOfficeConfig result = documentVersionService.previewVersion(
                requesterId,
                documentId,
                versionId,
                null,
                null
        );

        assertEquals("preview-token", result.getToken());
        verify(auditService).record(eq("VERSION_PREVIEWED"), any(Map.class));
    }

    @Test
    @DisplayName("Should download version bytes for authorized viewer")
    void downloadVersion_success() throws Exception {
        DocumentVersion version = buildVersion(versionId, 2, false, requesterId);
        byte[] fileBytes = "version-content".getBytes();

        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(permissionService.canView(document, requesterId, null)).thenReturn(true);
        doNothing().when(documentPasswordProtectionService)
                .requirePasswordForContentAccess(document, "secret", requesterId, null);
        when(documentVersionRepository.findByIdAndDocumentId(versionId, documentId)).thenReturn(Optional.of(version));
        when(fileStorageService.loadFile("Versions/v2/report.docx")).thenReturn(fileBytes);

        byte[] downloaded = documentVersionService
                .downloadVersion(requesterId, documentId, versionId, "secret", null)
                .getInputStream()
                .readAllBytes();

        assertEquals("version-content", new String(downloaded));
        verify(auditService).record(eq("VERSION_DOWNLOADED"), any(Map.class));
    }

    @Test
    @DisplayName("Should resolve download filename with version prefix")
    void resolveDownloadFilename_success() {
        DocumentVersion version = buildVersion(versionId, 4, false, requesterId);

        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(permissionService.canView(document, requesterId, null)).thenReturn(true);
        when(documentVersionRepository.findByIdAndDocumentId(versionId, documentId)).thenReturn(Optional.of(version));

        String filename = documentVersionService.resolveDownloadFilename(requesterId, documentId, versionId);

        assertEquals("v4_Report.docx", filename);
    }

    @Test
    @DisplayName("Should restore selected version and create backup snapshot")
    void restoreVersion_success() {
        DocumentVersion selectedVersion = buildVersion(versionId, 2, true, requesterId);
        selectedVersion.setStorageKey("Versions/v2/report.docx");
        selectedVersion.setSizeBytes(4096L);

        DocumentVersion backupVersion = DocumentVersion.builder()
                .id(UUID.randomUUID())
                .documentId(documentId)
                .versionNumber(3)
                .storageKey("Versions/v3/report.docx")
                .fileUrl("https://example.com/v3")
                .sizeBytes(4096L)
                .editedBy(requesterId)
                .editedAt(LocalDateTime.now())
                .build();

        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(permissionService.canRestore(document, requesterId)).thenReturn(true);
        when(documentVersionRepository.findByIdAndDocumentId(versionId, documentId)).thenReturn(Optional.of(selectedVersion));
        when(editSaveService.createRestoreBackupSnapshot(
                document,
                requesterId,
                2,
                "Versions/v2/report.docx"
        )).thenReturn(backupVersion);
        when(documentRepository.save(document)).thenReturn(document);

        RestoreVersionResponse response = documentVersionService.restoreVersion(requesterId, documentId, versionId);

        assertEquals(documentId, response.getDocumentId());
        assertEquals(versionId, response.getRestoredFromVersionId());
        assertEquals(2, response.getRestoredFromVersionNumber());
        assertNotNull(response.getRestoredAt());

        verify(fileStorageService).deleteFile("docs/report.docx");
        verify(fileStorageService).copyFile("Versions/v2/report.docx", "docs/report.docx");
        verify(documentVersionRepository).save(backupVersion);
        assertEquals("Restored from version 2", backupVersion.getChangeSummary());
        assertTrue(backupVersion.isCurrentVersionSnapshot());
        assertTrue(backupVersion.isRestored());
        assertFalse(selectedVersion.isCurrentVersionSnapshot());
        verify(auditService).record(eq("VERSION_RESTORED"), any(Map.class));
    }

    @Test
    @DisplayName("Should reject restore when user lacks permission")
    void restoreVersion_unauthorized() {
        when(documentRepository.findByIdAndDeletedFalse(documentId)).thenReturn(Optional.of(document));
        when(permissionService.canRestore(document, requesterId)).thenReturn(false);

        assertThrows(
                UnauthorizedAccessException.class,
                () -> documentVersionService.restoreVersion(requesterId, documentId, versionId)
        );
        verify(editSaveService, never()).createRestoreBackupSnapshot(any(), any(), any(Integer.class), any());
    }

    private DocumentVersion buildVersion(UUID id, int versionNumber, boolean current, Long editedBy) {
        return DocumentVersion.builder()
                .id(id)
                .documentId(documentId)
                .versionNumber(versionNumber)
                .storageKey("Versions/v" + versionNumber + "/report.docx")
                .fileUrl("https://example.com/v" + versionNumber)
                .sizeBytes(1024L)
                .editedBy(editedBy)
                .editedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .changeSummary("Version " + versionNumber)
                .currentVersionSnapshot(current)
                .restored(false)
                .build();
    }
}
