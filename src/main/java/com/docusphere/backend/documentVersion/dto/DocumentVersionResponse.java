package com.docusphere.backend.documentVersion.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class DocumentVersionResponse {

    private UUID versionId;
    private Integer versionNumber;
    private Long editedById;
    private String editedByName;
    private String editedByEmail;
    private boolean editedViaShareInvitation;
    private String editedByRole;
    private LocalDateTime editedAt;
    private LocalDateTime createdAt;
    private String changeSummary;
    private boolean isCurrent;
    private boolean isLatest;
    private boolean isRestored;
    private boolean isProtected;
    private Long fileSize;
}