package com.docusphere.backend.documentVersion.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class RestoreVersionResponse {

    private UUID documentId;
    private UUID restoredFromVersionId;
    private Integer restoredFromVersionNumber;
    private UUID backupVersionId;
    private Integer backupVersionNumber;
    private LocalDateTime restoredAt;
}