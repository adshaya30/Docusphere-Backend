package com.docusphere.backend.Common.response;

import lombok.*;

@Getter @Setter @AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long refreshTokenExpiry;
    private boolean rememberMe;
    private String role;
    private String fullName;
    private String email;
    private Long userId;
    private String profilePictureUrl;
}
