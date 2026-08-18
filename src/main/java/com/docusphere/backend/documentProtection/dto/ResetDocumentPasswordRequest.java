package com.docusphere.backend.documentProtection.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetDocumentPasswordRequest {

    @NotBlank(message = "newPassword is required")
    private String newPassword;

    @NotBlank(message = "accountPassword is required")
    private String accountPassword;
}


