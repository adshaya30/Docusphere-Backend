package com.docusphere.backend.documentVersion.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateVersionRequest {

    private String changeSummary;
    private String summary;

    public String resolveSummary() {
        if (changeSummary != null && !changeSummary.isBlank()) {
            return changeSummary.trim();
        }
        if (summary != null && !summary.isBlank()) {
            return summary.trim();
        }
        return null;
    }
}
