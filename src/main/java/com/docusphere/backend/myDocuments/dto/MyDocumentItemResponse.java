package com.docusphere.backend.myDocuments.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

// DTO representing a single document item in My Documents view
// Includes metadata and user-specific state like 'starred'
@Getter
@Builder
public class MyDocumentItemResponse {

    // Identifiers
    private String id;
    private String fileId;

    // Basic info
    private String name;
    private String type;
    private Long sizeBytes;

    // Ownership
    private String ownerId;
    private String teamId;

    // Flags
    private boolean starred;
    private boolean secured;

    // Status
    private UploadStatus status;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum UploadStatus {
        UPLOADING,
        COMPLETED,
        FAILED
    }
}