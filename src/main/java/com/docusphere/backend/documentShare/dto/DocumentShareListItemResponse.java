package com.docusphere.backend.documentShare.dto;

import com.docusphere.backend.documentShare.entity.DocumentSharePermission;
import com.docusphere.backend.documentShare.entity.ShareLinkType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class DocumentShareListItemResponse {

    private UUID shareLinkId;
    private String token;
    private ShareLinkType type;
    private DocumentSharePermission permission;
    private String invitedEmail;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
