package com.docusphere.backend.documentShare.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ShareInviteStatusResponse {
    private UUID documentId;
    private String email;
    private boolean signupRequired;
}
