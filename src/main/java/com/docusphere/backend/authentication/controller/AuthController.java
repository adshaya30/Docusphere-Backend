package com.docusphere.backend.authentication.controller;

import com.docusphere.backend.Common.response.AuthResponse;
import com.docusphere.backend.Common.response.AuthMeResponse;
import com.docusphere.backend.Common.response.MessageResponse;
import com.docusphere.backend.authentication.dto.*;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.authentication.service.UserService;
import com.docusphere.backend.authentication.service.security.CustomUserDetailsService;
import com.docusphere.backend.Common.exception.UserNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private static final String ACCESS_TOKEN_COOKIE = "accessToken";
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String REFRESH_COOKIE_PATH = "/api/auth/refresh";
    private static final String COOKIE_SAME_SITE = "None";

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;

    @Value("${jwt.access.expiration}")
    private long accessTokenCookieMaxAge;

    @Value("${app.session.remember-me-expiry}")
    private long rememberMeRefreshCookieMaxAge;

    @PostMapping("/signUp")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody SignUpRequest request) {
        userService.signUp(request);
        return ResponseEntity.ok(new MessageResponse("Please check your email to verify your account.", 200));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@RequestParam String token) {
        User user = userService.verifyEmail(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails, user.getRole().getName(), user.getId());
        String refreshToken = jwtService.generateRefreshToken(userDetails, user.getId());

        return withAuthCookies(
                accessToken,
                refreshToken,
                user,
                user.getRole().getName().replace("ROLE_", ""),
                true
        );
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

            UserDetails principal = (UserDetails) authentication.getPrincipal();
            String accessToken = jwtService.generateAccessToken(
                    principal,
                    user.getRole().getName(),
                    user.getId());

            boolean rememberMe = request.isRememberMe();
            String refreshToken = jwtService.generateRefreshToken(principal, user.getId(), rememberMe);

            return withAuthCookies(
                    accessToken,
                    refreshToken,
                    user,
                    user.getRole().getName().replace("ROLE_", ""),
                    rememberMe
            );
        } catch (UserNotFoundException e) {

            throw new BadCredentialsException("Email not registered. Please sign up first.");
        } catch (BadCredentialsException e) {

            if (e.getMessage().contains("Email not verified")) {
                throw e;
            }

            throw new BadCredentialsException("Invalid email or password. Please try again.");
        }

    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException("Refresh token is missing.");
        }

        if (!"REFRESH".equals(jwtService.extractRole(refreshToken))) {
            throw new BadCredentialsException("Invalid refresh token.");
        }

        String email = jwtService.extractUsername(refreshToken);
        User user = userService.findByEmail(email);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        Long tokenUserId = jwtService.extractUserId(refreshToken);
        if (tokenUserId != null && !tokenUserId.equals(user.getId())) {
            throw new BadCredentialsException("Invalid refresh token.");
        }

        String newAccessToken = jwtService.generateAccessToken(userDetails, user.getRole().getName(), user.getId());
        boolean rememberMe = jwtService.extractRememberMe(refreshToken);

        return withAuthCookies(
                newAccessToken,
                refreshToken,
                user,
                user.getRole().getName().replace("ROLE_", ""),
                rememberMe
        );
    }

    @PostMapping("/signOut")
    public ResponseEntity<MessageResponse> signOut() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, clearCookie(ACCESS_TOKEN_COOKIE, "/").toString());
        headers.add(HttpHeaders.SET_COOKIE, clearCookie(REFRESH_TOKEN_COOKIE, REFRESH_COOKIE_PATH).toString());

        return ResponseEntity.ok()
                .headers(headers)
                .body(new MessageResponse("Signed out successfully.", 200));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthMeResponse> me(
            @AuthenticationPrincipal UserDetails userDetails,
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {

        String email = null;
        if (userDetails != null && userDetails.getUsername() != null && !userDetails.getUsername().isBlank()) {
            email = userDetails.getUsername();
        } else if (accessToken != null && !accessToken.isBlank()) {
            email = jwtService.extractUsername(accessToken);
        }

        if (email == null || email.isBlank()) {
            throw new BadCredentialsException("No active session.");
        }

        User user = userService.findByEmail(email);

        if (accessToken != null && !accessToken.isBlank()) {
            UserDetails tokenUserDetails = userDetailsService.loadUserByUsername(email);
            if (!jwtService.isTokenValid(accessToken, tokenUserDetails)) {
                throw new BadCredentialsException("Invalid session.");
            }
        }

        Long refreshTokenExpiry = null;
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenExpiry = jwtService.extractExpiration(refreshToken).getTime();
        }

        return ResponseEntity.ok(new AuthMeResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName().replace("ROLE_", ""),
                user.getProfilePictureUrl(),
                refreshTokenExpiry
        ));
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
    @PutMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {

        userService.changePassword(userDetails.getUsername(), request);
        return ResponseEntity.ok(new MessageResponse("Password changed successfully", 200));
    }
    @PutMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<User> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @ModelAttribute UpdateProfileRequest request) throws Exception {

        User updatedUser = userService.updateProfile(userDetails.getUsername(), request);
        return ResponseEntity.ok(updatedUser);
    }

    private ResponseEntity<AuthResponse> withAuthCookies(String accessToken, String refreshToken, User user, String role, boolean rememberMe) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, createCookie(ACCESS_TOKEN_COOKIE, accessToken, "/", Duration.ofMillis(accessTokenCookieMaxAge)).toString());
        headers.add(HttpHeaders.SET_COOKIE, rememberMe
                ? createCookie(REFRESH_TOKEN_COOKIE, refreshToken, REFRESH_COOKIE_PATH, Duration.ofMillis(rememberMeRefreshCookieMaxAge)).toString()
                : createSessionCookie(REFRESH_TOKEN_COOKIE, refreshToken, REFRESH_COOKIE_PATH).toString());

        return ResponseEntity.ok()
                .headers(headers)
                .body(new AuthResponse(
                        accessToken,
                        refreshToken,
                        role,
                        user.getFullName(),
                        user.getEmail(),
                        user.getId(),
                        user.getProfilePictureUrl()
                ));
    }

    private ResponseCookie createCookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite(COOKIE_SAME_SITE)
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie createSessionCookie(String name, String value, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite(COOKIE_SAME_SITE)
                .path(path)
                .build();
    }

    private ResponseCookie clearCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite(COOKIE_SAME_SITE)
                .path(path)
                .maxAge(Duration.ZERO)
                .build();
    }

}
