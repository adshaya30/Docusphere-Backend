package com.docusphere.backend.onlyoffice.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class SharedEditorConfigResponse {

    private UUID documentId;
    private String invitedEmail;
    private OnlyOfficeConfig config;
}
