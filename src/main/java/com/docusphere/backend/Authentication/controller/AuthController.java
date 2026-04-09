package com.docusphere.backend.Authentication.controller;

import com.docusphere.backend.Authentication.dto.AuthResponse;
import com.docusphere.backend.Authentication.dto.MessageResponse;
import com.docusphere.backend.Authentication.dto.SignInRequest;
import com.docusphere.backend.Authentication.dto.SignUpRequest;
import com.docusphere.backend.Authentication.entity.User;
import com.docusphere.backend.Authentication.service.JwtService;
import com.docusphere.backend.Authentication.service.UserService;
import com.docusphere.backend.Authentication.service.security.CustomUserDetailsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;

    @PostMapping("/signUp")
    public ResponseEntity<com.docusphere.backend.Authentication.dto.MessageResponse> register(@Valid @RequestBody SignUpRequest request) {
        userService.signUp(request);
        return ResponseEntity.ok(new MessageResponse("Please check your email to verify your account."));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@RequestParam String token) {
        User user = userService.verifyEmail(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String jwt = jwtService.generateToken(userDetails, user.getRole().getName());
        
        return ResponseEntity.ok(new AuthResponse(
            jwt,
            user.getRole().getName().replace("ROLE_", ""),
            user.getFullName(),
            user.getEmail()
        ));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerificationEmail(@RequestParam String email) {
        userService.resendVerificationEmail(email);
        return ResponseEntity.ok(new MessageResponse("Verification email has been sent. Please check your inbox."));
    }

    @PostMapping("/signIn")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody SignInRequest request) {
        try {
            // Check if user exists and is verified
            User user = userService.findByEmail(request.getEmail());

            if (!user.isEnabled()) {
                throw new BadCredentialsException("Email not verified. Please check your inbox for verification link.");
            }

            // Authenticate user
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            String jwt = jwtService.generateToken((org.springframework.security.core.userdetails.User) authentication.getPrincipal(), user.getRole().getName());

            return ResponseEntity.ok(new AuthResponse(jwt, user.getRole().getName().replace("ROLE_", ""), user.getFullName(), user.getEmail()));
        } catch (com.docusphere.backend.exception.UserNotFoundException e) {
            // User not found - provide helpful message to sign up
            throw new BadCredentialsException("Email not registered. Please sign up first.");
        } catch (BadCredentialsException e) {
            // If it's our custom message, throw it as is
            if (e.getMessage().contains("Email not verified")) {
                throw e;
            }
            // Otherwise, it's from authentication manager (wrong password)
            throw new BadCredentialsException("Invalid email or password. Please try again.");
        }
    }

}
