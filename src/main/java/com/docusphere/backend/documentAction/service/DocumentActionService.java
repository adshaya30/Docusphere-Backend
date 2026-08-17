package com.docusphere.backend.documentAction.service;

import com.docusphere.backend.documentAction.dto.DocumentActionResponse;
import com.docusphere.backend.documentAction.dto.TrashDocumentsPageResponse;
import org.springframework.core.io.Resource;

import java.util.UUID;

public interface DocumentActionService {

    DocumentActionResponse rename(Long requesterId, UUID documentId, String newName);

    DocumentActionResponse move(Long requesterId, UUID documentId, UUID targetTeamId);

    DocumentActionResponse duplicate(Long requesterId, UUID documentId);

    DocumentActionResponse moveToTrash(Long requesterId, UUID documentId);

    DocumentActionResponse restoreFromTrash(Long requesterId, UUID documentId);

    void permanentlyDelete(Long requesterId, UUID documentId);

    TrashDocumentsPageResponse getTrash(Long requesterId, int page, int size);

    Resource download(Long requesterId, UUID documentId, String password);

    Resource downloadForSystem(UUID documentId);

    String resolveDownloadFilename(Long requesterId, UUID documentId, String password);

    Resource downloadByShareToken(UUID documentId, String token, String password);

    String resolveDownloadFilenameByShareToken(UUID documentId, String token, String password);

    Resource downloadByOnlyOfficeToken(UUID documentId, String dlToken, String urlShareToken);

    String resolveDownloadFilenameByOnlyOfficeToken(UUID documentId, String dlToken, String urlShareToken);
}