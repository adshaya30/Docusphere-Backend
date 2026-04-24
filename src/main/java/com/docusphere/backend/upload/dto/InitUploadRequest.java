package com.docusphere.backend.upload.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InitUploadRequest {

    @NotBlank(message = "fileName is required")
    private String fileName;

    @NotNull(message = "fileSize is required")
    @Positive(message = "fileSize must be greater than 0")
    private Long fileSize;
}