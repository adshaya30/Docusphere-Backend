package com.docusphere.backend.authentication.dto;

import lombok.*;

@Getter @Setter
public class OAuth2LoginRequest {
    private String provider;
    private String idToken;
    private String name;
    private String email;
    private String picture;
}
