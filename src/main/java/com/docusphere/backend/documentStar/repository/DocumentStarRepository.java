package com.docusphere.backend.documentStar.repository;


import org.springframework.data.jpa.repository.Modifying;
import com.docusphere.backend.documentStar.entity.DocumentStar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentStarRepository extends JpaRepository<DocumentStar, UUID> {

    boolean existsByUserIdAndDocumentId(Long userId, UUID documentId);

    Optional<DocumentStar> findByUserIdAndDocumentId(Long userId, UUID documentId);

    List<DocumentStar> findByUserId(Long userId);

    void deleteByUserIdAndDocumentId(Long userId, UUID documentId);
    
    // MODIFIED: Method to count starred documents by user
    long countByUserId(Long userId);

    // MODIFIED: Added batch delete method for admin team merge feature (deletes all stars for documents in list)
    @Modifying
    void deleteByDocumentIdIn(List<UUID> documentIds);

    long countByUserId(Long userId);

}