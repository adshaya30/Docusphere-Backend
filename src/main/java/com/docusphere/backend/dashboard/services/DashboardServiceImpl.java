package com.docusphere.backend.dashboard.services;

import org.springframework.stereotype.Service;

import com.docusphere.backend.dashboard.dto.DashboardResponse;

@Service
public class DashboardServiceImpl implements DashboardService {

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long ownerId, int recentDays) {
        int safeRecentDays = Math.max(1, recentDays);
        java.time.LocalDateTime recentThreshold = java.time.LocalDateTime.now().minusDays(safeRecentDays);

        // Total Documents
        long total = entityManager.createQuery(
                "SELECT COUNT(d) FROM Document d WHERE d.ownerId = :ownerId AND d.deleted = false", Long.class)
                .setParameter("ownerId", ownerId)
                .getSingleResult();

        // Recent Documents
        long recent = entityManager.createQuery(
                "SELECT COUNT(d) FROM Document d WHERE d.ownerId = :ownerId AND d.createdAt >= :threshold AND d.deleted = false", Long.class)
                .setParameter("ownerId", ownerId)
                .setParameter("threshold", recentThreshold)
                .getSingleResult();

        // Starred Documents
        long starred = entityManager.createQuery(
                "SELECT COUNT(s) FROM DocumentStar s WHERE s.userId = :ownerId", Long.class)
                .setParameter("ownerId", ownerId)
                .getSingleResult();

        // Completed Uploads
        long uploads = entityManager.createQuery(
                "SELECT COUNT(d) FROM Document d WHERE d.ownerId = :ownerId AND d.status = com.docusphere.backend.document.entity.Document.UploadStatus.COMPLETED AND d.deleted = false", Long.class)
                .setParameter("ownerId", ownerId)
                .getSingleResult();

        return new DashboardResponse(total, recent, starred, uploads);
    }
}
