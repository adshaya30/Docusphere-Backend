package com.docusphere.backend.dashboard.services;

import com.docusphere.backend.dashboard.dto.DashboardResponse;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentStar.repository.DocumentStarRepository;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final DocumentRepository documentRepository;
    private final DocumentStarRepository documentStarRepository;

    public DashboardServiceImpl(DocumentRepository documentRepository, DocumentStarRepository documentStarRepository) {
        this.documentRepository = documentRepository;
        this.documentStarRepository = documentStarRepository;
    }

    @Override
    public DashboardResponse getDashboard(Long ownerId, int recentDays) {
        int safeRecentDays = Math.max(1, recentDays);
        LocalDateTime recentThreshold = LocalDateTime.now().minusDays(safeRecentDays);

        long total = documentRepository.countByOwnerId(ownerId);
        long recent = documentRepository.count(ownerAndCreatedAfter(ownerId, recentThreshold));
        long starred = documentStarRepository.countByUserId(ownerId);
        long uploads = documentRepository.countByOwnerIdAndStatus(ownerId, Document.UploadStatus.COMPLETED);

        return new DashboardResponse(total, recent, starred, uploads);
    }

    private Specification<Document> ownerAndCreatedAfter(Long ownerId, LocalDateTime threshold) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("ownerId"), ownerId),
                cb.greaterThanOrEqualTo(root.get("createdAt"), threshold)
        );
    }
}
