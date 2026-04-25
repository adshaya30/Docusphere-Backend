package com.docusphere.backend.documentAction.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MoveRequest {

    @Pattern(
            regexp = "^[0-9a-fA-F-]{36}$",
            message = "teamId must be a valid UUID when provided"
    )
    private String teamId;
}