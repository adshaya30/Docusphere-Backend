package com.docusphere.backend.documentAction.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class TrashDocumentItemResponse {
    private UUID id;
    private String fileId;
    private String name;
    private String type;
    private Long sizeBytes;
    private Long ownerId;
    private UUID teamId;
    private String storageKey;
    private String fileUrl;
    private LocalDateTime deletedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
