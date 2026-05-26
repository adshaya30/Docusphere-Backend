package com.docusphere.backend.Common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuthMeResponse {
    private Long userId;
    private String email;
    private String fullName;
    private String role;
    private String profilePictureUrl;
    private Long refreshTokenExpiry;
}

