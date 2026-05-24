package com.docusphere.backend.documentAction.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class DocumentActionResponse {

    private UUID documentId;
    private String name;
    private Long ownerId;
    private UUID teamId;
    private String storageKey;
    private boolean deleted;
    private LocalDateTime deletedAt;
    private LocalDateTime updatedAt;
}