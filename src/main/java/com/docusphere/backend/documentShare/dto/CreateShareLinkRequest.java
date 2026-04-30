package com.docusphere.backend.documentShare.dto;

import com.docusphere.backend.documentShare.entity.DocumentSharePermission;
import com.docusphere.backend.documentShare.entity.ShareLinkType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CreateShareLinkRequest {

    @NotNull(message = "permission is required")
    private DocumentSharePermission permission;

    @NotNull(message = "type is required")
    private ShareLinkType type;

    private String email;

    private LocalDateTime expiresAt;
}
