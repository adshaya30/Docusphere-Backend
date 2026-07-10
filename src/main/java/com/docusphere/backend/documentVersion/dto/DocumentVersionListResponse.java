package com.docusphere.backend.documentVersion.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class DocumentVersionListResponse {

    private UUID documentId;
    private Integer currentVersionNumber;
    private boolean isProtected;
    private List<DocumentVersionResponse> versions;
}