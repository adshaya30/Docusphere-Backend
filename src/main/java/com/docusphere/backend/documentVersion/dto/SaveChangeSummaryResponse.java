package com.docusphere.backend.documentVersion.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class SaveChangeSummaryResponse {

    private UUID documentId;
    private String changeSummary;
}