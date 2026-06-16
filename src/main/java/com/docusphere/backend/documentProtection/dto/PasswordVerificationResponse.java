package com.docusphere.backend.documentProtection.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class PasswordVerificationResponse {

    private UUID documentId;
    private boolean verified;
}
