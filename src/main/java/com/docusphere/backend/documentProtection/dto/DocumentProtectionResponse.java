package com.docusphere.backend.documentProtection.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class DocumentProtectionResponse {

    private UUID documentId;
    private String name;
    private boolean passwordProtected;
    private LocalDateTime updatedAt;
}
