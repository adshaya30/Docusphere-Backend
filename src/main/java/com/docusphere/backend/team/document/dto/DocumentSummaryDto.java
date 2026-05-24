package com.docusphere.backend.team.document.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class DocumentSummaryDto {
    private UUID id;
    private String name;
    private String type;
    private Long sizeBytes;
    private Long ownerId;
    private String uploadedBy;
    private UUID teamId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
