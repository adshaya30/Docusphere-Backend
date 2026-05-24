package com.docusphere.backend.Common.response;

import lombok.*;

@Getter @Setter @AllArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private String role;
    private String fullName;
    private String email;
    private Long userId;
    private String profilePictureUrl;
}
