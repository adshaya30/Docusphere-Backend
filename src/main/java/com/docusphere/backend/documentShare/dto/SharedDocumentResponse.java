package com.docusphere.backend.documentShare.dto;

import com.docusphere.backend.documentShare.entity.DocumentSharePermission;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class SharedDocumentResponse {
    private UUID documentId;
    private String name;
    private String type;
    private Long sizeBytes;
    private String fileUrl;
    private DocumentSharePermission permission;
    private boolean canComment;
}
