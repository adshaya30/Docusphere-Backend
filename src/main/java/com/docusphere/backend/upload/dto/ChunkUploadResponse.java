package com.docusphere.backend.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChunkUploadResponse {

    private final boolean completed;

    private final String documentId;
}