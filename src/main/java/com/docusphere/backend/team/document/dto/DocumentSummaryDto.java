package com.docusphere.backend.team.document.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DocumentSummaryDto {
    private UUID id;
    private String name;
    private String type;
    private Long sizeBytes;
    private Boolean starred;
    private Long ownerId;
    private String uploadedBy;
    private UUID teamId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
