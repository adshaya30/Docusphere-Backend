package com.docusphere.backend.documentShare.dto;

import com.docusphere.backend.documentShare.entity.DocumentSharePermission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class ShareRequest {

    @NotEmpty(message = "emails are required")
    private List<@NotNull @Email(message = "invalid email format") String> emails;

    @NotNull(message = "permission is required")
    private DocumentSharePermission permission;
}
