package com.docusphere.backend.documentStar.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DocumentStarResponse {

    private java.util.UUID documentId;
    private Long userId;
    private boolean starred;
    private LocalDateTime starredAt;
}