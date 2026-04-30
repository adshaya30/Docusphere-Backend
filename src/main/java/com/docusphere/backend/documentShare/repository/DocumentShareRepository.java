package com.docusphere.backend.documentShare.repository;

import com.docusphere.backend.documentShare.entity.DocumentShare;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface DocumentShareRepository extends JpaRepository<DocumentShare, UUID> {

    Optional<DocumentShare> findByToken(String token);

    Optional<DocumentShare> findByDocumentIdAndEmailIgnoreCaseAndRevokedFalse(UUID documentId, String email);

    boolean existsByDocumentIdAndEmailIgnoreCaseAndRevokedFalse(UUID documentId, String email);

    void deleteByDocumentId(UUID documentId);

    default boolean isExpired(DocumentShare share, LocalDateTime now) {
        return share.getExpiresAt() != null && share.getExpiresAt().isBefore(now);
    }
}
