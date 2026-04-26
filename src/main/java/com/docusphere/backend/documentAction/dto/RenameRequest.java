package com.docusphere.backend.documentAction.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RenameRequest {

    @NotBlank(message = "newName is required")
    private String newName;
}