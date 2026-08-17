package com.docusphere.backend.authentication.controller;

import com.docusphere.backend.Common.exception.*;
import com.docusphere.backend.authentication.dto.*;
import com.docusphere.backend.authentication.entity.Role;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.authentication.service.UserService;
import com.docusphere.backend.authentication.service.security.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUpMocks() {
        when(jwtService.extractExpiration(anyString())).thenReturn(new java.util.Date(System.currentTimeMillis() + 3600000));
    }

    @AfterEach
    void resetMocks() {
        reset(userService, jwtService, authenticationManager, userDetailsService);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public UserService userService() {
            return mock(UserService.class);
        }

        @Bean
        @Primary
        public JwtService jwtService() {
            return mock(JwtService.class);
        }

        @Bean
        @Primary
        public AuthenticationManager authenticationManager() {
            return mock(AuthenticationManager.class);
        }

        @Bean
        @Primary
        public CustomUserDetailsService customUserDetailsService() {
            return mock(CustomUserDetailsService.class);
        }
    }

    // ===================== SIGN UP TESTS =====================

    @Test
    @DisplayName("POST /api/auth/signUp - Success with valid request")
    void signUp_withValidRequest_shouldReturnSuccess() throws Exception {
        SignUpRequest request = createSignUpRequest("John Doe", "john@example.com", "Password@123", "Password@123");

        mockMvc.perform(post("/api/auth/signUp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Please check your email to verify your account."))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andDo(print());

        verify(userService).signUp(any(SignUpRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/signUp - Fail when passwords do not match")
    void signUp_whenPasswordsDoNotMatch_shouldReturnBadRequest() throws Exception {
        SignUpRequest request = createSignUpRequest("John Doe", "john@example.com", "Password@123", "Different@123");

        doThrow(new InvalidPasswordException("Passwords do not match"))
                .when(userService).signUp(any(SignUpRequest.class));

        mockMvc.perform(post("/api/auth/signUp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/signUp - Fail when email already exists")
    void signUp_whenEmailAlreadyExists_shouldReturnConflict() throws Exception {
        SignUpRequest request = createSignUpRequest("John Doe", "existing@example.com", "Password@123", "Password@123");

        doThrow(new EmailAlreadyExistsException("Email already registered"))
                .when(userService).signUp(any(SignUpRequest.class));

        mockMvc.perform(post("/api/auth/signUp")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/signUp - Fail with missing required fields")
    void signUp_withMissingFields_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/signUp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verify(userService, never()).signUp(any());
    }

    // ===================== SIGN IN TESTS =====================

    @Test
    @DisplayName("POST /api/auth/signIn - Success with valid credentials")
    void signIn_withValidCredentials_shouldReturnAuthResponse() throws Exception {
        SignInRequest request = createSignInRequest("john@example.com", "Password@123", false);
        User user = createUser(1L, "john@example.com", "John Doe", true, "ROLE_USER");
        UserDetails userDetails = mock(UserDetails.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        when(userService.findByEmail("john@example.com")).thenReturn(user);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtService.generateAccessToken(userDetails, "ROLE_USER", 1L)).thenReturn("access-token-value");
        when(jwtService.generateRefreshToken(any(UserDetails.class), eq(1L), anyBoolean())).thenReturn("refresh-token-value");

        mockMvc.perform(post("/api/auth/signIn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-value"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-value"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.fullName").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(result -> {
                    var cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
                    assertTrue(cookies.stream().anyMatch(value -> value.contains("accessToken=access-token-value")));
                    assertTrue(cookies.stream().anyMatch(value -> value.startsWith("refreshToken=") && !value.contains("Max-Age")));
                    assertTrue(cookies.stream().anyMatch(value -> value.contains("HttpOnly")));
                    
                    assertTrue(cookies.stream().anyMatch(value -> value.contains("SameSite=")));
                })
                .andDo(print());

        verify(userService).findByEmail("john@example.com");
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("POST /api/auth/signIn - Fail when email not registered")
    void signIn_whenUserNotFound_shouldReturnUnauthorized() throws Exception {
        SignInRequest request = createSignInRequest("notfound@example.com", "Password@123", false);

        when(userService.findByEmail("notfound@example.com"))
                .thenThrow(new UserNotFoundException("User not found"));

        mockMvc.perform(post("/api/auth/signIn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/signIn - Fail when email not verified")
    void signIn_whenEmailNotVerified_shouldReturnUnauthorized() throws Exception {
        SignInRequest request = createSignInRequest("john@example.com", "Password@123", false);
        User user = createUser(1L, "john@example.com", "John Doe", false, "ROLE_USER");

        when(userService.findByEmail("john@example.com")).thenReturn(user);

        mockMvc.perform(post("/api/auth/signIn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(containsString("Email not verified")))
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/signIn - Fail with invalid password")
    void signIn_withInvalidPassword_shouldReturnUnauthorized() throws Exception {
        SignInRequest request = createSignInRequest("john@example.com", "WrongPassword", false);
        User user = createUser(1L, "john@example.com", "John Doe", true, "ROLE_USER");

        when(userService.findByEmail("john@example.com")).thenReturn(user);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/signIn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password. Please try again."))
                .andDo(print());
    }

        @Test
        @DisplayName("POST /api/auth/signIn - Return normal invalid credentials after lock expiry when password is still wrong")
        void signIn_afterLockExpiryWithWrongPassword_shouldReturnInvalidCredentials() throws Exception {
        SignInRequest request = createSignInRequest("john@example.com", "WrongPassword", false);
        User user = createUser(1L, "john@example.com", "John Doe", true, "ROLE_USER");
        user.setAccountLocked(true);
        user.setLockedUntil(java.time.LocalDateTime.now().minusMinutes(1));

        when(userService.findByEmail("john@example.com")).thenReturn(user);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/signIn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password. Please try again."))
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/signIn - Return lockout response after threshold is reached")
    void signIn_whenLockoutThresholdReached_shouldReturnLockedResponse() throws Exception {
        SignInRequest request = createSignInRequest("john@example.com", "WrongPassword", false);
        User user = createUser(1L, "john@example.com", "John Doe", true, "ROLE_USER");

        when(userService.findByEmail("john@example.com")).thenReturn(user);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));
        when(userService.handleFailedLoginAttempt(user)).thenReturn(true);

        mockMvc.perform(post("/api/auth/signIn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.message").value(containsString("temporarily locked")))
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/signIn - Success with rememberMe flag")
    void signIn_withRememberMeTrue_shouldGenerateTokenWithFlag() throws Exception {
        SignInRequest request = createSignInRequest("john@example.com", "Password@123", true);
        User user = createUser(1L, "john@example.com", "John Doe", true, "ROLE_USER");
        UserDetails userDetails = mock(UserDetails.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);

        when(userService.findByEmail("john@example.com")).thenReturn(user);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(jwtService.generateAccessToken(userDetails, "ROLE_USER", 1L)).thenReturn("access-token-long-expiry");
        when(jwtService.generateRefreshToken(any(UserDetails.class), eq(1L), anyBoolean())).thenReturn("refresh-token-long-expiry");

        mockMvc.perform(post("/api/auth/signIn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-long-expiry"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-long-expiry"))
                .andExpect(result -> {
                    var cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
                    assertTrue(cookies.stream().anyMatch(value -> value.startsWith("refreshToken=") && value.contains("Max-Age=604800")));
                })
                .andDo(print());

        verify(jwtService).generateAccessToken(userDetails, "ROLE_USER", 1L);
        verify(jwtService).generateRefreshToken(any(UserDetails.class), eq(1L), eq(true));
    }

    // ===================== SIGN OUT TESTS =====================

    @Test
    @DisplayName("POST /api/auth/signOut - Success")
    void signOut_shouldReturnSuccessMessage() throws Exception {
        mockMvc.perform(post("/api/auth/signOut")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Signed out successfully."))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(result -> {
                    var cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
                    assertTrue(cookies.stream().anyMatch(value -> value.startsWith("accessToken=") && value.contains("Max-Age=0")));
                    assertTrue(cookies.stream().anyMatch(value -> value.startsWith("refreshToken=") && value.contains("Max-Age=0")));
                    assertTrue(cookies.stream().anyMatch(value -> value.contains("HttpOnly")));
                    assertTrue(cookies.stream().anyMatch(value -> value.contains("SameSite=")));
                })
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/refresh - Success with valid refresh cookie")
    void refresh_withValidRefreshCookie_shouldReturnNewAccessToken() throws Exception {
        User user = createUser(1L, "john@example.com", "John Doe", true, "ROLE_USER");
        UserDetails userDetails = mock(UserDetails.class);

        when(jwtService.extractRole("refresh-token-value")).thenReturn("REFRESH");
        when(jwtService.extractUsername("refresh-token-value")).thenReturn("john@example.com");
        when(jwtService.extractUserId("refresh-token-value")).thenReturn(1L);
        when(jwtService.extractRememberMe("refresh-token-value")).thenReturn(false);
        when(userService.findByEmail("john@example.com")).thenReturn(user);
        when(userDetailsService.loadUserByUsername("john@example.com")).thenReturn(userDetails);
        when(jwtService.generateAccessToken(userDetails, "ROLE_USER", 1L)).thenReturn("new-access-token");

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", "refresh-token-value"))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-value"))
                .andDo(print());

        verify(jwtService).generateAccessToken(userDetails, "ROLE_USER", 1L);
    }

    @Test
    @DisplayName("POST /api/auth/refresh - Fail when refresh cookie is missing")
    void refresh_withoutRefreshCookie_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/refresh")
                .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andDo(print());
    }

    // ===================== AUTH ME TESTS =====================

    @Test
    @DisplayName("GET /api/auth/me - Success with authenticated user and refresh cookie")
    @WithMockUser(username = "john@example.com", roles = "USER")
    void me_withAuthenticatedUser_shouldReturnSessionInfo() throws Exception {
        User user = createUser(1L, "john@example.com", "John Doe", true, "ROLE_USER");

        when(userService.findByEmail("john@example.com")).thenReturn(user);
        when(jwtService.extractExpiration("refresh-token-value")).thenReturn(new java.util.Date(123456789L));

        mockMvc.perform(get("/api/auth/me")
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", "refresh-token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.fullName").value("John Doe"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.profilePictureUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.refreshTokenExpiry").value(123456789L))
                .andDo(print());

        verify(userService).findByEmail("john@example.com");
        verify(jwtService).extractExpiration("refresh-token-value");
    }

    @Test
    @DisplayName("GET /api/auth/me - Success with access token cookie")
    void me_withAccessTokenCookie_shouldReturnSessionInfo() throws Exception {
        User user = createUser(2L, "jane@example.com", "Jane Doe", true, "ROLE_USER");
        UserDetails tokenUserDetails = mock(UserDetails.class);

        when(jwtService.extractUsername("access-token-value")).thenReturn("jane@example.com");
        when(userService.findByEmail("jane@example.com")).thenReturn(user);
        when(userDetailsService.loadUserByUsername("jane@example.com")).thenReturn(tokenUserDetails);
        when(jwtService.isTokenValid("access-token-value", tokenUserDetails)).thenReturn(true);
        when(jwtService.extractExpiration("refresh-token-value")).thenReturn(new java.util.Date(987654321L));

        mockMvc.perform(get("/api/auth/me")
                .cookie(new jakarta.servlet.http.Cookie("accessToken", "access-token-value"),
                        new jakarta.servlet.http.Cookie("refreshToken", "refresh-token-value")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(2))
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.fullName").value("Jane Doe"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.refreshTokenExpiry").value(987654321L))
                .andDo(print());

        verify(jwtService).extractUsername("access-token-value");
        verify(userDetailsService).loadUserByUsername("jane@example.com");
        verify(jwtService).isTokenValid("access-token-value", tokenUserDetails);
    }

    // ===================== VERIFY EMAIL TESTS =====================

    @Test
    @DisplayName("GET /api/auth/verify-email - Success with valid token")
    void verifyEmail_withValidToken_shouldReturnAuthResponse() throws Exception {
        User user = createUser(5L, "john@example.com", "John Doe", true, "ROLE_USER");
        UserDetails userDetails = mock(UserDetails.class);

        when(userService.verifyEmail("valid-token")).thenReturn(user);
        when(userDetailsService.loadUserByUsername("john@example.com")).thenReturn(userDetails);
        when(jwtService.generateAccessToken(userDetails, "ROLE_USER", 5L)).thenReturn("access-token-after-verification");
        when(jwtService.generateRefreshToken(userDetails, 5L)).thenReturn("refresh-token-after-verification");

        mockMvc.perform(get("/api/auth/verify-email").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token-after-verification"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-after-verification"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.fullName").value("John Doe"))
                .andExpect(jsonPath("$.userId").value(5))
                .andDo(print());

        verify(userService).verifyEmail("valid-token");
    }

    @Test
    @DisplayName("GET /api/auth/verify-email - Fail with invalid token")
    void verifyEmail_withInvalidToken_shouldReturnError() throws Exception {
        when(userService.verifyEmail("invalid-token")).thenThrow(new InvalidTokenException("Token is invalid"));

        mockMvc.perform(get("/api/auth/verify-email").param("token", "invalid-token"))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("GET /api/auth/verify-email - Fail with expired token")
    void verifyEmail_withExpiredToken_shouldReturnError() throws Exception {
        when(userService.verifyEmail("expired-token")).thenThrow(new TokenExpiredException("Token has expired"));

        mockMvc.perform(get("/api/auth/verify-email").param("token", "expired-token"))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("GET /api/auth/verify-email - Fail when token parameter missing")
    void verifyEmail_withoutToken_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/auth/verify-email"))
                .andExpect(status().isBadRequest())
                .andDo(print());
    }

    // ===================== RESEND VERIFICATION EMAIL TESTS =====================

    @Test
    @DisplayName("POST /api/auth/resend-verification - Success")
    void resendVerificationEmail_withValidEmail_shouldReturnSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification")
                .param("email", "john@example.com")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verification email has been sent. Please check your inbox."))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andDo(print());

        verify(userService).resendVerificationEmail("john@example.com");
    }

    @Test
    @DisplayName("POST /api/auth/resend-verification - Fail when user not found")
    void resendVerificationEmail_whenUserNotFound_shouldReturnError() throws Exception {
        doThrow(new UserNotFoundException("User not found"))
                .when(userService).resendVerificationEmail("notfound@example.com");

        mockMvc.perform(post("/api/auth/resend-verification")
                .param("email", "notfound@example.com")
                .with(csrf()))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }


    // ===================== FORGOT PASSWORD TESTS =====================

    @Test
    @DisplayName("POST /api/auth/forgot-password - Success with valid email")
    void forgotPassword_withValidEmail_shouldReturnSuccess() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("john@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If your email is registered, you will receive a password reset link."))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andDo(print());

        verify(userService).forgotPassword("john@example.com");
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password - Fail when user not found")
    void forgotPassword_whenUserNotFound_shouldReturnError() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("notfound@example.com");

        doThrow(new UserNotFoundException("User not found"))
                .when(userService).forgotPassword("notfound@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password - Fail when user not verified")
    void forgotPassword_whenUserNotVerified_shouldReturnError() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("john@example.com");

        doThrow(new EmailNotVerifiedException("Email not verified"))
                .when(userService).forgotPassword("john@example.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password - Fail with missing email")
    void forgotPassword_withMissingEmail_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verify(userService, never()).forgotPassword(anyString());
    }

    // ===================== RESET PASSWORD TESTS =====================

    @Test
    @DisplayName("POST /api/auth/reset-password - Success with valid token and matching passwords")
    void resetPassword_withValidRequest_shouldReturnSuccess() throws Exception {
        ResetPasswordRequest request = createResetPasswordRequest("valid-token", "NewPassword@123", "NewPassword@123");

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Your password has been reset successfully. You can now login."))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andDo(print());

        verify(userService).resetPassword(any(ResetPasswordRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/reset-password - Fail when passwords do not match")
    void resetPassword_whenPasswordsMismatch_shouldReturnBadRequest() throws Exception {
        ResetPasswordRequest request = createResetPasswordRequest("valid-token", "NewPassword@123", "Different@123");

        doThrow(new InvalidPasswordException("Passwords do not match"))
                .when(userService).resetPassword(any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/reset-password - Fail with invalid token")
    void resetPassword_withInvalidToken_shouldReturnError() throws Exception {
        ResetPasswordRequest request = createResetPasswordRequest("invalid-token", "NewPassword@123", "NewPassword@123");

        doThrow(new InvalidTokenException("Token is invalid"))
                .when(userService).resetPassword(any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/reset-password - Fail with expired token")
    void resetPassword_withExpiredToken_shouldReturnError() throws Exception {
        ResetPasswordRequest request = createResetPasswordRequest("expired-token", "NewPassword@123", "NewPassword@123");

        doThrow(new TokenExpiredException("Token has expired"))
                .when(userService).resetPassword(any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/reset-password - Fail when new password is same as old password")
    void resetPassword_whenNewPasswordSameAsOld_shouldReturnBadRequest() throws Exception {
        ResetPasswordRequest request = createResetPasswordRequest("valid-token", "OldPassword@123", "OldPassword@123");

        doThrow(new InvalidPasswordException("New password must be different from current password"))
                .when(userService).resetPassword(any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/reset-password - Fail with missing fields")
    void resetPassword_withMissingFields_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verify(userService, never()).resetPassword(any());
    }

    // ===================== CHANGE PASSWORD TESTS =====================

    @Test
    @DisplayName("PUT /api/auth/change-password - Success with valid request and correct current password")
    @WithMockUser(username = "user@example.com", roles = "USER")
    void changePassword_withValidRequest_shouldReturnSuccess() throws Exception {
        ChangePasswordRequest request = createChangePasswordRequest("OldPassword@123", "NewPassword@456", "NewPassword@456");

        mockMvc.perform(put("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andDo(print());

        verify(userService).changePassword(eq("user@example.com"), any(ChangePasswordRequest.class));
    }

    @Test
    @DisplayName("PUT /api/auth/change-password - Fail when current password is incorrect")
    @WithMockUser(username = "user@example.com", roles = "USER")
    void changePassword_whenCurrentPasswordIncorrect_shouldReturnBadRequest() throws Exception {
        ChangePasswordRequest request = createChangePasswordRequest("WrongPassword@123", "NewPassword@456", "NewPassword@456");

        doThrow(new InvalidPasswordException("Current password is incorrect"))
                .when(userService).changePassword(anyString(), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("PUT /api/auth/change-password - Fail when new passwords do not match")
    @WithMockUser(username = "user@example.com", roles = "USER")
    void changePassword_whenNewPasswordsDoNotMatch_shouldReturnBadRequest() throws Exception {
        ChangePasswordRequest request = createChangePasswordRequest("OldPassword@123", "NewPassword@456", "Different@789");

        doThrow(new InvalidPasswordException("New passwords do not match"))
                .when(userService).changePassword(anyString(), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("PUT /api/auth/change-password - Fail when new password same as current password")
    @WithMockUser(username = "user@example.com", roles = "USER")
    void changePassword_whenNewPasswordSameAsCurrent_shouldReturnBadRequest() throws Exception {
        ChangePasswordRequest request = createChangePasswordRequest("OldPassword@123", "OldPassword@123", "OldPassword@123");

        doThrow(new InvalidPasswordException("New password must be different from your current password"))
                .when(userService).changePassword(anyString(), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("PUT /api/auth/change-password - Fail when user not found")
    @WithMockUser(username = "user@example.com", roles = "USER")
    void changePassword_whenUserNotFound_shouldReturnNotFound() throws Exception {
        ChangePasswordRequest request = createChangePasswordRequest("OldPassword@123", "NewPassword@456", "NewPassword@456");

        doThrow(new UserNotFoundException("User not found"))
                .when(userService).changePassword(anyString(), any(ChangePasswordRequest.class));

        mockMvc.perform(put("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .with(csrf()))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("PUT /api/auth/change-password - Fail when validation fails")
    @WithMockUser(username = "user@example.com", roles = "USER")
    void changePassword_withMissingFields_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(put("/api/auth/change-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verify(userService, never()).changePassword(anyString(), any());
    }

    // ===================== HELPER METHODS =====================

    private SignUpRequest createSignUpRequest(String fullName, String email, String password, String confirmPassword) {
        SignUpRequest request = new SignUpRequest();
        request.setFullName(fullName);
        request.setEmail(email);
        request.setPassword(password);
        request.setConfirmPassword(confirmPassword);
        return request;
    }

    private SignInRequest createSignInRequest(String email, String password, boolean rememberMe) {
        SignInRequest request = new SignInRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setRememberMe(rememberMe);
        return request;
    }

    private ResetPasswordRequest createResetPasswordRequest(String token, String newPassword, String confirmPassword) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken(token);
        request.setNewPassword(newPassword);
        request.setConfirmPassword(confirmPassword);
        return request;
    }

    private ChangePasswordRequest createChangePasswordRequest(String currentPassword, String newPassword, String confirmNewPassword) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        request.setConfirmNewPassword(confirmNewPassword);
        return request;
    }

    private User createUser(Long id, String email, String fullName, boolean enabled, String roleName) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setFullName(fullName);
        user.setPassword("encoded-password");
        user.setEnabled(enabled);
        Role role = new Role();
        role.setId(1L);
        role.setName(roleName);
        user.setRole(role);
        return user;
    }

}
