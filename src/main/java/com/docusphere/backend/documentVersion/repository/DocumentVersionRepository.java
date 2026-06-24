package com.docusphere.backend.documentVersion.repository;

import com.docusphere.backend.documentVersion.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    Optional<DocumentVersion> findByIdAndDocumentId(UUID id, UUID documentId);

    @Query("SELECT MAX(v.versionNumber) FROM DocumentVersion v WHERE v.documentId = :documentId")
    Optional<Integer> findMaxVersionNumberByDocumentId(@Param("documentId") UUID documentId);

    Optional<DocumentVersion> findFirstByDocumentIdOrderByVersionNumberDesc(UUID documentId);

    @Query("""
            SELECT COUNT(v) > 0 FROM DocumentVersion v
            WHERE v.documentId = :documentId
              AND v.editedBy = :editedBy
              AND v.createdAt > :since
            """)
    boolean existsRecentVersionByDocumentIdAndEditedBy(
            @Param("documentId") UUID documentId,
            @Param("editedBy") Long editedBy,
            @Param("since") LocalDateTime since
    );
}
