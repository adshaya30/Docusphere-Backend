package com.docusphere.backend.document.repository;
import com.docusphere.backend.document.entity.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
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

    boolean existsByOwnerIdAndNameAndDeletedFalseAndIdNot(Long ownerId, String name, UUID id);

    boolean existsByOwnerIdAndTeamIdAndNameAndDeletedFalseAndIdNot(Long ownerId, UUID teamId, String name, UUID id);

    Optional<Document> findByIdAndDeletedFalse(UUID id);

    List<Document> findByDeletedTrueAndDeletedAtBefore(LocalDateTime cutoff);

    long countByOwnerId(Long ownerId);

    long countByOwnerIdAndStatus(Long ownerId, Document.UploadStatus status);

    Page<Document> findByOwnerIdAndTeamIdAndDeletedFalse(Long ownerId, UUID teamId, Pageable pageable);

    List<Document> findAllByTeamId(UUID teamId);

    long countByTeamIdAndDeletedFalse(UUID teamId);

    boolean existsByOwnerIdAndUpdatedAtAfter(Long ownerId, LocalDateTime cutoff);

    // Update ALL documents that belong to oldTeamId and move them to newTeamId(MERGE TEAM : ADMIN)
    @Modifying
    @Query("UPDATE Document d SET d.teamId = :newTeamId WHERE d.teamId = :oldTeamId")
    void updateAllTeamId(@Param("oldTeamId") UUID oldTeamId, @Param("newTeamId") UUID newTeamId);

    @Modifying
    void deleteByTeamId(UUID teamId);

    @Query("SELECT SUM(d.sizeBytes) FROM Document d")
    Long sumTotalStorageBytes();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Document d WHERE d.id = :id AND d.deleted = false")
    Optional<Document> findActiveByIdForUpdate(@Param("id") UUID id);
}
