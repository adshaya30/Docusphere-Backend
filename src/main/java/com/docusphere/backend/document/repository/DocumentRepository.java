package com.docusphere.backend.document.repository;

import com.docusphere.backend.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID>,
        JpaSpecificationExecutor<Document> {

    Page<Document> findByOwnerId(Long ownerId, Pageable pageable);

    Page<Document> findByTeamId(UUID teamId, Pageable pageable);

    Optional<Document> findByFileId(String fileId);
}