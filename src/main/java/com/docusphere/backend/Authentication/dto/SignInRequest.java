package com.docusphere.backend.Authentication.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter

public class SignInRequest {
    @NotBlank(message="Email is required")
    @Email(message="Invalid Email format")
    private String email;

    @NotBlank(message="Password is required")
    private String password;
}
