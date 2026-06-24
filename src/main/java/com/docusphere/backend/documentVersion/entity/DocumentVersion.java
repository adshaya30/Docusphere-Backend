package com.docusphere.backend.documentVersion.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "document_versions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_document_version_number",
                columnNames = {"document_id", "version_number"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "file_url", nullable = false)
    private String fileUrl;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "edited_by", nullable = false)
    private Long editedBy;

    @Column(name = "edited_at", nullable = false)
    private LocalDateTime editedAt;

    @Column(name = "change_summary")
    private String changeSummary;

    @Column(name = "is_current", nullable = false)
    private boolean currentVersionSnapshot = false;

    @Column(name = "is_restored", nullable = false)
    private boolean restored = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void onPrePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (editedAt == null) {
            editedAt = LocalDateTime.now();
        }
    }
}
