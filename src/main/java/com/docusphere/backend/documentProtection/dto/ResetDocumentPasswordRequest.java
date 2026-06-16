package com.docusphere.backend.documentProtection.dto;

import jakarta.validation.constraints.NotBlank;
// ...existing imports...
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetDocumentPasswordRequest {

    @NotBlank(message = "newPassword is required")
    private String newPassword;
}


