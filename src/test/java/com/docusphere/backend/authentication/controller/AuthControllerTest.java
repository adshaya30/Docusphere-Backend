package com.docusphere.backend.authentication.controller;

import com.docusphere.backend.Common.exception.*;
import com.docusphere.backend.authentication.dto.*;
import com.docusphere.backend.authentication.entity.Role;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.authentication.service.UserService;
import com.docusphere.backend.authentication.service.security.CustomUserDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
                .content(objectMapper.writeValueAsString(request)))
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
                .content(objectMapper.writeValueAsString(request)))
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
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/signUp - Fail with missing required fields")
    void signUp_withMissingFields_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/signUp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
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
        when(jwtService.generateToken(userDetails, "ROLE_USER", 1L, false)).thenReturn("jwt-token-value");

        mockMvc.perform(post("/api/auth/signIn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-value"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.fullName").value("John Doe"))
                .andExpect(jsonPath("$.email").value("john@example.com"))
                .andExpect(jsonPath("$.userId").value(1))
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
                .content(objectMapper.writeValueAsString(request)))
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
                .content(objectMapper.writeValueAsString(request)))
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
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
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
        when(jwtService.generateToken(userDetails, "ROLE_USER", 1L, true)).thenReturn("jwt-token-long-expiry");

        mockMvc.perform(post("/api/auth/signIn")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-long-expiry"))
                .andDo(print());

        verify(jwtService).generateToken(userDetails, "ROLE_USER", 1L, true);
    }

    // ===================== SIGN OUT TESTS =====================

    @Test
    @DisplayName("POST /api/auth/signOut - Success")
    void signOut_shouldReturnSuccessMessage() throws Exception {
        mockMvc.perform(post("/api/auth/signOut"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Signed out successfully."))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andDo(print());
    }

    // ===================== VERIFY EMAIL TESTS =====================

    @Test
    @DisplayName("GET /api/auth/verify-email - Success with valid token")
    void verifyEmail_withValidToken_shouldReturnAuthResponse() throws Exception {
        User user = createUser(5L, "john@example.com", "John Doe", true, "ROLE_USER");
        UserDetails userDetails = mock(UserDetails.class);

        when(userService.verifyEmail("valid-token")).thenReturn(user);
        when(userDetailsService.loadUserByUsername("john@example.com")).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails, "ROLE_USER", 5L)).thenReturn("jwt-token-after-verification");

        mockMvc.perform(get("/api/auth/verify-email").param("token", "valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-after-verification"))
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
        mockMvc.perform(post("/api/auth/resend-verification").param("email", "john@example.com"))
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

        mockMvc.perform(post("/api/auth/resend-verification").param("email", "notfound@example.com"))
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
                .content(objectMapper.writeValueAsString(request)))
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
                .content(objectMapper.writeValueAsString(request)))
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
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError())
                .andDo(print());
    }

    @Test
    @DisplayName("POST /api/auth/forgot-password - Fail with missing email")
    void forgotPassword_withMissingEmail_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
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
                .content(objectMapper.writeValueAsString(request)))
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
                .content(objectMapper.writeValueAsString(request)))
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
                .content(objectMapper.writeValueAsString(request)))
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
                .content(objectMapper.writeValueAsString(request)))
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
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andDo(print());

        verify(userService, never()).resetPassword(any());
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

