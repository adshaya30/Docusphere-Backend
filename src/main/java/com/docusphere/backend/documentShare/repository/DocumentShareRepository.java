package com.docusphere.backend.documentShare.repository;

import com.docusphere.backend.documentShare.entity.DocumentShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
// MODIFIED: Added List import for deleteByDocumentIdIn parameter
import java.util.List;
import java.util.Optional;
// MODIFIED: Added @Modifying annotation import for batch delete operations
import org.springframework.data.jpa.repository.Modifying;
import java.util.UUID;

public interface DocumentShareRepository extends JpaRepository<DocumentShare, UUID> {

    Optional<DocumentShare> findByToken(String token);

    Optional<DocumentShare> findByDocumentIdAndEmailIgnoreCaseAndRevokedFalse(UUID documentId, String email);

    boolean existsByDocumentIdAndEmailIgnoreCaseAndRevokedFalse(UUID documentId, String email);

    void deleteByDocumentId(UUID documentId);

    List<DocumentShare> findByDocumentIdAndRevokedFalseOrderByCreatedAtDesc(UUID documentId);

    default boolean isExpired(DocumentShare share, LocalDateTime now) {
        return share.getExpiresAt() != null && share.getExpiresAt().isBefore(now);
    }

    // MODIFIED: Added batch delete method for admin merge team feature (deletes all shares for documents in list)
    @Modifying
    void deleteByDocumentIdIn(List<UUID> documentIds);
}
