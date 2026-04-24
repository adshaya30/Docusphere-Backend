package com.docusphere.backend.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InitUploadResponse {
    private final String fileId;
}