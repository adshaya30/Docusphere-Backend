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

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    private static final String ACCESS_TOKEN_COOKIE = "accessToken";
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String REFRESH_COOKIE_PATH = "/api/auth/refresh";
    private static final String COOKIE_SAME_SITE_PROD = "None";
    private static final String COOKIE_SAME_SITE_DEV = "Lax";

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;

    @Value("${jwt.access.expiration}")
    private long accessTokenCookieMaxAge;

    @Value("${app.session.remember-me-expiry}")
    private long rememberMeRefreshCookieMaxAge;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

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
                true,
                jwtService.extractExpiration(refreshToken).getTime()
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

            // Check and auto-unlock if lock time has passed
            if (user.getLockedUntil() != null) {
                if (user.getLockedUntil().isBefore(LocalDateTime.now())) {
                    // Time has passed → auto unlock
                    userService.handleAutoUnlock(user);
                } else {
                    // Still locked
                    throw new com.docusphere.backend.Common.exception.AccountLockedException("Account is locked. Try again in 15 minutes.", user.getLockedUntil());
                }
            }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            userService.handleSuccessfulLoginAttempt(user);

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
                    rememberMe,
                    jwtService.extractExpiration(refreshToken).getTime()
            );
        } catch (UserNotFoundException e) {

            throw new BadCredentialsException("Email not registered. Please sign up first.");
        } catch (BadCredentialsException e) {

            if (e.getMessage().contains("Email not verified")) {
                throw e;
            }

            if (e.getMessage() != null && e.getMessage().contains("temporarily locked")) {
                throw e;
            }

            try {
                User user = userService.findByEmail(request.getEmail());
                boolean lockedNow = userService.handleFailedLoginAttempt(user);
                if (lockedNow) {
                    throw new com.docusphere.backend.Common.exception.AccountLockedException("Account is temporarily locked due to multiple failed login attempts. Please try again later or reset your password.", user.getLockedUntil());
                }
            } catch (UserNotFoundException ignored) {
                // ignore missing users
            }

            throw new BadCredentialsException("Invalid email or password. Please try again.");
        }

    }

    @PostMapping("/oauth2/login")
    public ResponseEntity<AuthResponse> oauth2Login(@Valid @RequestBody OAuth2LoginRequest request) {
        User user = userService.processOAuth2User(
                request.getEmail(),
                request.getName(),
                request.getPicture(),
                request.getProvider()
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails, user.getRole().getName(), user.getId());
        String refreshToken = jwtService.generateRefreshToken(userDetails, user.getId());

        return withAuthCookies(
                accessToken,
                refreshToken,
                user,
                user.getRole().getName().replace("ROLE_", ""),
            true,
            jwtService.extractExpiration(refreshToken).getTime()
        );
    }

    @GetMapping("/google")
    public ResponseEntity<Void> redirectToGoogle() {
        return redirectToOAuth2Provider("google");
    }

    @GetMapping("/github")
    public ResponseEntity<Void> redirectToGithub() {
        return redirectToOAuth2Provider("github");
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
            rememberMe,
            jwtService.extractExpiration(refreshToken).getTime()
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
    @PostMapping("/verify-password")
    public ResponseEntity<MessageResponse> verifyPassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
            @RequestBody java.util.Map<String, String> body) {

        String email = null;
        if (userDetails != null && userDetails.getUsername() != null && !userDetails.getUsername().isBlank()) {
            email = userDetails.getUsername();
        } else if (accessToken != null && !accessToken.isBlank()) {
            email = jwtService.extractUsername(accessToken);
        }

        if (email == null || email.isBlank()) {
            throw new BadCredentialsException("No active session.");
        }

        String password = body != null ? body.get("password") : null;
        if (password == null || password.isBlank()) {
            throw new BadCredentialsException("Password is required.");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, password)
        );

        return ResponseEntity.ok(new MessageResponse("Password verified successfully.", 200));
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
        @CookieValue(name = ACCESS_TOKEN_COOKIE, required = false) String accessToken,
        @ModelAttribute UpdateProfileRequest request) throws Exception {

    String email = null;

    if (userDetails != null && userDetails.getUsername() != null && !userDetails.getUsername().isBlank()) {
        email = userDetails.getUsername();
    } else if (accessToken != null && !accessToken.isBlank()) {
        email = jwtService.extractUsername(accessToken);
    }

    if (email == null || email.isBlank()) {
        throw new BadCredentialsException("No active session.");
    }

    User updatedUser = userService.updateProfile(email, request);
    return ResponseEntity.ok(updatedUser);
}

    private ResponseEntity<AuthResponse> withAuthCookies(String accessToken, String refreshToken, User user, String role, boolean rememberMe, Long refreshTokenExpiry) {
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
                    refreshTokenExpiry,
                    rememberMe,
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
                .secure(isCookieSecure())
                .sameSite(getCookieSameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie createSessionCookie(String name, String value, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(isCookieSecure())
                .sameSite(getCookieSameSite())
                .path(path)
                .build();
    }

    private ResponseCookie clearCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(isCookieSecure())
                .sameSite(getCookieSameSite())
                .path(path)
                .maxAge(Duration.ZERO)
                .build();
    }

    private boolean isCookieSecure() {
        return frontendUrl != null && frontendUrl.startsWith("https://");
    }

    private String getCookieSameSite() {
        return isCookieSecure() ? COOKIE_SAME_SITE_PROD : COOKIE_SAME_SITE_DEV;
    }

    private ResponseEntity<Void> redirectToOAuth2Provider(String provider) {
        return ResponseEntity.status(302)
                .location(URI.create("/oauth2/authorize/" + provider))
                .build();
    }

}
