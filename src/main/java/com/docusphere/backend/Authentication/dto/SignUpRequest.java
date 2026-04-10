package com.docusphere.backend.Authentication.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SignUpRequest {
    @NotBlank(message="Full name is required")
    private String fullName;

    @NotBlank(message="Email is required")
    @Email(message="Invalid email format")
    private String email;

    @NotBlank(message="Password is required")
    @Size(min=8,message="Password must be at leaset 8 characters")

    @Pattern(regexp = ".*\\d.*", message = "Password must contain at least 1 number")
    @Pattern(regexp = ".*[a-z].*", message = "Password must contain at least 1 lowercase letter")
    @Pattern(regexp = ".*[A-Z].*", message = "Password must contain at least 1 uppercase letter")
    @Pattern(regexp = ".*[@#$%!].*", message = "Password must contain at least 1 special character (@ # $ % !)")

    private String password;

    @NotBlank(message = "Confirm password is required")
    private String confirmPassword;

}
