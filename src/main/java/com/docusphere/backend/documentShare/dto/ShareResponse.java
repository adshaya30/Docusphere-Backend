package com.docusphere.backend.documentShare.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ShareResponse {
    private int invitedCount;
    private String accessLink;
}
