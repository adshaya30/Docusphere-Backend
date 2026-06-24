package com.docusphere.backend.document.service;

import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.documentVersion.service.DocumentEditSaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentUpdateService {

    private final DocumentEditSaveService editSaveService;

    @Transactional
    public Document saveEditedDocument(
            UUID documentId,
            MultipartFile file,
            Long editedBy,
            String changeSummary,
            String idempotencyKey
    ) throws IOException {
        return editSaveService.processMultipartSave(
                documentId,
                file,
                editedBy,
                changeSummary,
                idempotencyKey
        );
    }
}
