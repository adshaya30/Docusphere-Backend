package com.docusphere.backend.document.repository;

import com.docusphere.backend.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID>,
        JpaSpecificationExecutor<Document> {

    Page<Document> findByOwnerIdAndDeletedFalse(Long ownerId, Pageable pageable);

    Page<Document> findByOwnerIdAndDeletedTrue(Long ownerId, Pageable pageable);

    Page<Document> findByTeamIdAndDeletedFalse(UUID teamId, Pageable pageable);

    Optional<Document> findByFileId(String fileId);

    boolean existsByOwnerIdAndNameAndDeletedFalse(Long ownerId, String name);

    boolean existsByOwnerIdAndTeamIdAndNameAndDeletedFalse(Long ownerId, UUID teamId, String name);

    Optional<Document> findByIdAndDeletedFalse(UUID id);

    List<Document> findByDeletedTrueAndDeletedAtBefore(LocalDateTime cutoff);
}