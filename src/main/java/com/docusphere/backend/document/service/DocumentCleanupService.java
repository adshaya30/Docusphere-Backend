package com.docusphere.backend.document.service;

import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import com.docusphere.backend.documentShare.repository.DocumentShareRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class DocumentCleanupService {

    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final DocumentShareRepository documentShareRepository;

    public DocumentCleanupService(DocumentRepository documentRepository,
                                  FileStorageService fileStorageService,
                                  DocumentShareRepository documentShareRepository) {
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.documentShareRepository = documentShareRepository;
    }

    // Runs daily at 2 AM server time.
    @Scheduled(cron = "${app.document.cleanup.cron:0 0 2 * * *}")
    @Transactional
    public void purgeSoftDeletedDocuments() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<Document> expired = documentRepository.findByDeletedTrueAndDeletedAtBefore(cutoff);

        for (Document document : expired) {
            try {
                fileStorageService.deleteFile(document.getStorageKey());
            } catch (Exception ex) {
                log.warn("Failed to delete storage file for document {}", document.getId(), ex);
            }
        }

        List<UUID> docIds = expired.stream().map(Document::getId).toList();
        if (!docIds.isEmpty()) {
            documentShareRepository.deleteByDocumentIdIn(docIds);
        }

        documentRepository.deleteAll(expired);
        if (!expired.isEmpty()) {
            log.info("Purged {} documents from trash older than 30 days", expired.size());
        }
    }
}
