package com.docusphere.backend.documentProtection.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DocumentPasswordRequest {

    @NotBlank(message = "password is required")
    private String password;
}
