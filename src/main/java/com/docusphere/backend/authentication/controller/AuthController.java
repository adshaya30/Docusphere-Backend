package com.docusphere.backend.authentication.controller;

import com.docusphere.backend.Common.response.AuthResponse;
import com.docusphere.backend.Common.response.MessageResponse;
import com.docusphere.backend.authentication.dto.SignInRequest;
import com.docusphere.backend.authentication.dto.SignUpRequest;
import com.docusphere.backend.authentication.dto.ForgotPasswordRequest;
import com.docusphere.backend.authentication.dto.ResetPasswordRequest;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.authentication.service.UserService;
import com.docusphere.backend.authentication.service.security.CustomUserDetailsService;
import com.docusphere.backend.Common.exception.UserNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class AuthController {
    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;

    @PostMapping("/signUp")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody SignUpRequest request) {
        userService.signUp(request);
        return ResponseEntity.ok(new MessageResponse("Please check your email to verify your account.", 200));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@RequestParam String token) {
        User user = userService.verifyEmail(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String jwt = jwtService.generateToken(userDetails, user.getRole().getName(), user.getId());

        return ResponseEntity.ok(new AuthResponse(
                jwt,
                user.getRole().getName().replace("ROLE_", ""),
                user.getFullName(),
                user.getEmail(),
                user.getId()
        ));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerificationEmail(@RequestParam String email) {
        userService.resendVerificationEmail(email);
        return ResponseEntity.ok(new MessageResponse("Verification email has been sent. Please check your inbox.", 200));
    }

    @PostMapping("/signIn")
    public ResponseEntity<AuthResponse> signIn(@Valid @RequestBody SignInRequest request) {
        try {
            User user = userService.findByEmail(request.getEmail());

            if (!user.isEnabled()) {
                throw new BadCredentialsException("Email not verified. Please check your inbox for verification link.");
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            String jwt = jwtService.generateToken(
                    (org.springframework.security.core.userdetails.User) authentication.getPrincipal(),
                    user.getRole().getName(),
                    user.getId(),
                    request.isRememberMe());

            return ResponseEntity.ok(new AuthResponse(jwt, user.getRole().getName().replace("ROLE_", ""), user.getFullName(), user.getEmail(), user.getId()));
        } catch (UserNotFoundException e) {

            throw new BadCredentialsException("Email not registered. Please sign up first.");
        } catch (BadCredentialsException e) {

            if (e.getMessage().contains("Email not verified")) {
                throw e;
            }

            throw new BadCredentialsException("Invalid email or password. Please try again.");
        }

    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        userService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(new MessageResponse("If your email is registered, you will receive a password reset link.", 200));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            userService.resetPassword(request);
            return ResponseEntity.ok(new MessageResponse("Your password has been reset successfully. You can now login.", 200));
        } catch (Exception e) {
            throw e;
        }
    }

}
