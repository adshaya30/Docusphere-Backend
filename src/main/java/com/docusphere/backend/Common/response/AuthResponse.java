package com.docusphere.backend.common.response;

import lombok.*;

@Getter @Setter @AllArgsConstructor
public class AuthResponse {
    private String token;
    private String role;
    private String fullName;
    private String email;
    private Long userId;
}
