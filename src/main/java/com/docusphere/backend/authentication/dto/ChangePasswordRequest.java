package com.docusphere.backend.authentication.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter @Setter
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @Size(min = 8, message = "New password must be at least 8 characters")
    @Size(min=8,message="Password must be at least 8 characters")
    @Pattern(regexp = ".*\\d.*", message = "Password must contain at least 1 number")
    @Pattern(regexp = ".*[a-z].*", message = "Password must contain at least 1 lowercase letter")
    @Pattern(regexp = ".*[A-Z].*", message = "Password must contain at least 1 uppercase letter")
    @Pattern(regexp = ".*[@#$%!].*", message = "Password must contain at least 1 special character (@ # $ % !)")
    private String newPassword;

    @NotBlank(message = "Confirm password is required")
    @Size(min=8,message="Confirm password must be at least 8 characters")
    private String confirmNewPassword;
}
