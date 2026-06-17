package com.docusphere.backend.authentication.controller;

import com.docusphere.backend.Common.response.MessageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
public class OAuth2LoginRedirectController {

    @GetMapping("/login")
    public ResponseEntity<MessageResponse> loginPage(
            @RequestParam(name = "error", required = false) String error) {
        if (error != null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("OAuth login failed. Check the provider callback URL, session cookie/state, or GitHub email access.", 401));
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new MessageResponse("Choose a provider using /api/auth/google or /api/auth/github.", 400));
    }

    @GetMapping("/oauth2/authorization/{provider}")
    public ResponseEntity<Void> redirectDefaultAuthorizationPath(@PathVariable String provider) {
        return redirectToAuthorizationProvider(provider);
    }

    @GetMapping("/login/oauth2/authorization/{provider}")
    public ResponseEntity<Void> redirectLoginAuthorizationPath(@PathVariable String provider) {
        return redirectToAuthorizationProvider(provider);
    }

    private ResponseEntity<Void> redirectToAuthorizationProvider(String provider) {
        return ResponseEntity.status(302)
                .location(URI.create("/oauth2/authorize/" + provider))
                .build();
    }
}
