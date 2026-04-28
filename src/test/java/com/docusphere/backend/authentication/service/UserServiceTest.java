package com.docusphere.backend.authentication.service;

import com.docusphere.backend.Common.config.AdminConfig;
import com.docusphere.backend.Common.config.AppConfig;
import com.docusphere.backend.Common.exception.EmailAlreadyExistsException;
import com.docusphere.backend.Common.exception.EmailNotVerifiedException;
import com.docusphere.backend.Common.exception.InvalidPasswordException;
import com.docusphere.backend.Common.exception.InvalidTokenException;
import com.docusphere.backend.Common.exception.TokenExpiredException;
import com.docusphere.backend.Common.exception.UserNotFoundException;
import com.docusphere.backend.authentication.dto.ResetPasswordRequest;
import com.docusphere.backend.authentication.dto.SignUpRequest;
import com.docusphere.backend.authentication.entity.PasswordResetToken;
import com.docusphere.backend.authentication.entity.Role;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.entity.VerificationToken;
import com.docusphere.backend.authentication.repository.PasswordResetTokenRepository;
import com.docusphere.backend.authentication.repository.RoleRepository;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.repository.VerificationTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private VerificationTokenRepository tokenRepository;

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AdminConfig adminConfig;

    @Mock
    private AppConfig appConfig;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("signUp fails when password and confirm password do not match")
    void signUp_whenPasswordsDoNotMatch_shouldThrowInvalidPasswordException() {
        SignUpRequest request = signUpRequest("Test User", "test@example.com", "Password@123", "Different@123");

        assertThrows(InvalidPasswordException.class, () -> userService.signUp(request));

        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("signUp fails when email is already registered")
    void signUp_whenEmailAlreadyExists_shouldThrowEmailAlreadyExistsException() {
        SignUpRequest request = signUpRequest("Test User", "test@example.com", "Password@123", "Password@123");
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> userService.signUp(request));

        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("signUp succeeds for valid request and saves user, token, and sends verification email")
    void signUp_whenValidRequest_shouldSaveUserAndSendEmail() throws Exception {
        SignUpRequest request = signUpRequest("New User", "NEWUSER@example.com", "Password@123", "Password@123");
        Role role = role("ROLE_USER");

        when(userRepository.existsByEmail("newuser@example.com")).thenReturn(false);
        when(adminConfig.getAdminEmails()).thenReturn(List.of());
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("Password@123")).thenReturn("encoded-password");
        when(appConfig.getFrontendUrl()).thenReturn("http://localhost:5173");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(100L);
            return u;
        });

        userService.signUp(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertEquals("New User", savedUser.getFullName());
        assertEquals("newuser@example.com", savedUser.getEmail());
        assertEquals("encoded-password", savedUser.getPassword());
        assertFalse(savedUser.isEnabled());
        assertEquals("ROLE_USER", savedUser.getRole().getName());

        ArgumentCaptor<VerificationToken> tokenCaptor = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository, times(1)).save(tokenCaptor.capture());
        VerificationToken savedToken = tokenCaptor.getValue();

        assertNotNull(savedToken.getToken());
        assertNotNull(savedToken.getUser());
        assertEquals("newuser@example.com", savedToken.getUser().getEmail());
        assertTrue(savedToken.getExpiryDate().isAfter(LocalDateTime.now()));

        verify(emailService, timeout(1000).times(1))
                .sendVerificationEmail(eq("newuser@example.com"), contains("/verify-email?token="));
    }

    @Test
    @DisplayName("findByEmail returns user when email exists")
    void findByEmail_whenFound_shouldReturnUser() {
        User user = user("user@example.com", "encoded", true, role("ROLE_USER"));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        User result = userService.findByEmail("USER@example.com");

        assertSame(user, result);
    }

    @Test
    @DisplayName("findByEmail throws when email does not exist")
    void findByEmail_whenNotFound_shouldThrowUserNotFoundException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.findByEmail("missing@example.com"));
    }

    @Test
    @DisplayName("verifyEmail throws when token is invalid")
    void verifyEmail_whenTokenInvalid_shouldThrowInvalidTokenException() {
        when(tokenRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> userService.verifyEmail("bad-token"));
    }

    @Test
    @DisplayName("verifyEmail throws when token is expired")
    void verifyEmail_whenTokenExpired_shouldThrowTokenExpiredException() {
        VerificationToken vt = new VerificationToken();
        vt.setToken("expired-token");
        vt.setUser(user("user@example.com", "encoded", false, role("ROLE_USER")));
        vt.setExpiryDate(LocalDateTime.now().minusMinutes(1));

        when(tokenRepository.findByToken("expired-token")).thenReturn(Optional.of(vt));

        assertThrows(TokenExpiredException.class, () -> userService.verifyEmail("expired-token"));
    }

    @Test
    @DisplayName("verifyEmail enables user and deletes token for valid token")
    void verifyEmail_whenValid_shouldEnableUserAndDeleteToken() {
        User user = user("user@example.com", "encoded", false, role("ROLE_USER"));

        VerificationToken vt = new VerificationToken();
        vt.setToken("good-token");
        vt.setUser(user);
        vt.setExpiryDate(LocalDateTime.now().plusHours(1));

        when(tokenRepository.findByToken("good-token")).thenReturn(Optional.of(vt));

        User result = userService.verifyEmail("good-token");

        assertTrue(result.isEnabled());
        verify(userRepository, times(1)).save(user);
        verify(tokenRepository, times(1)).delete(vt);
    }

    @Test
    @DisplayName("resendVerificationEmail updates existing token and sends email")
    void resendVerificationEmail_whenTokenExists_shouldUpdateAndSendEmail() throws Exception {
        User user = user("user@example.com", "encoded", false, role("ROLE_USER"));
        VerificationToken existing = new VerificationToken();
        existing.setToken("old-token");
        existing.setUser(user);
        existing.setExpiryDate(LocalDateTime.now().minusHours(1));

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(tokenRepository.findByUser(user)).thenReturn(Optional.of(existing));
        when(appConfig.getFrontendUrl()).thenReturn("http://localhost:5173");

        userService.resendVerificationEmail("user@example.com");

        verify(tokenRepository, times(1)).save(existing);
        assertTrue(existing.getExpiryDate().isAfter(LocalDateTime.now()));

        verify(emailService, timeout(1000).times(1))
                .sendVerificationEmail(eq("user@example.com"), contains("/verify-email?token="));
    }

    @Test
    @DisplayName("forgotPassword throws when user does not exist")
    void forgotPassword_whenUserNotFound_shouldThrowUserNotFoundException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.forgotPassword("missing@example.com"));
    }

    @Test
    @DisplayName("forgotPassword throws when user is not verified")
    void forgotPassword_whenUserNotEnabled_shouldThrowEmailNotVerifiedException() {
        User user = user("user@example.com", "encoded", false, role("ROLE_USER"));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThrows(EmailNotVerifiedException.class, () -> userService.forgotPassword("user@example.com"));

        verify(passwordResetTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("forgotPassword saves reset token and sends email for verified user")
    void forgotPassword_whenValid_shouldSaveTokenAndSendEmail() throws Exception {
        User user = user("user@example.com", "encoded", true, role("ROLE_USER"));
        user.setId(77L);

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(appConfig.getFrontendUrl()).thenReturn("http://localhost:5173");

        userService.forgotPassword("USER@example.com");

        verify(passwordResetTokenRepository, times(1)).deleteByUserId(77L);

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository, times(1)).save(captor.capture());
        PasswordResetToken saved = captor.getValue();

        assertNotNull(saved.getToken());
        assertEquals(user, saved.getUser());
        assertTrue(saved.getExpiryDate().isAfter(LocalDateTime.now()));

        verify(emailService, timeout(1000).times(1))
                .sendPasswordResetEmail(eq("user@example.com"), contains("/reset-password?token="));
    }

    @Test
    @DisplayName("resetPassword throws when new and confirm password do not match")
    void resetPassword_whenPasswordsMismatch_shouldThrowInvalidPasswordException() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("token");
        req.setNewPassword("Password@123");
        req.setConfirmPassword("Different@123");

        assertThrows(InvalidPasswordException.class, () -> userService.resetPassword(req));
    }

    @Test
    @DisplayName("resetPassword throws when reset token is invalid")
    void resetPassword_whenTokenInvalid_shouldThrowInvalidTokenException() {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("bad-token");
        req.setNewPassword("Password@123");
        req.setConfirmPassword("Password@123");

        when(passwordResetTokenRepository.findByToken("bad-token")).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> userService.resetPassword(req));
    }

    @Test
    @DisplayName("resetPassword throws when reset token is expired")
    void resetPassword_whenTokenExpired_shouldThrowTokenExpiredException() {
        User user = user("user@example.com", "encoded", true, role("ROLE_USER"));
        PasswordResetToken token = new PasswordResetToken();
        token.setToken("expired");
        token.setUser(user);
        token.setExpiryDate(LocalDateTime.now().minusMinutes(1));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("expired");
        req.setNewPassword("Password@123");
        req.setConfirmPassword("Password@123");

        when(passwordResetTokenRepository.findByToken("expired")).thenReturn(Optional.of(token));

        assertThrows(TokenExpiredException.class, () -> userService.resetPassword(req));
    }

    @Test
    @DisplayName("resetPassword throws when new password equals current password")
    void resetPassword_whenNewPasswordSameAsOld_shouldThrowInvalidPasswordException() {
        User user = user("user@example.com", "old-encoded", true, role("ROLE_USER"));

        PasswordResetToken token = new PasswordResetToken();
        token.setToken("good-token");
        token.setUser(user);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(10));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("good-token");
        req.setNewPassword("Password@123");
        req.setConfirmPassword("Password@123");

        when(passwordResetTokenRepository.findByToken("good-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("Password@123", "old-encoded")).thenReturn(true);

        assertThrows(InvalidPasswordException.class, () -> userService.resetPassword(req));
    }

    @Test
    @DisplayName("resetPassword updates password and deletes token when request is valid")
    void resetPassword_whenValid_shouldUpdatePasswordAndDeleteToken() {
        User user = user("user@example.com", "old-encoded", true, role("ROLE_USER"));

        PasswordResetToken token = new PasswordResetToken();
        token.setToken("good-token");
        token.setUser(user);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(10));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("good-token");
        req.setNewPassword("Password@123");
        req.setConfirmPassword("Password@123");

        when(passwordResetTokenRepository.findByToken("good-token")).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("Password@123", "old-encoded")).thenReturn(false);
        when(passwordEncoder.encode("Password@123")).thenReturn("new-encoded");

        userService.resetPassword(req);

        assertEquals("new-encoded", user.getPassword());
        verify(userRepository, times(1)).save(user);
        verify(passwordResetTokenRepository, times(1)).delete(token);
    }

    private SignUpRequest signUpRequest(String fullName, String email, String password, String confirmPassword) {
        SignUpRequest request = new SignUpRequest();
        request.setFullName(fullName);
        request.setEmail(email);
        request.setPassword(password);
        request.setConfirmPassword(confirmPassword);
        return request;
    }

    private Role role(String name) {
        Role role = new Role();
        role.setId(1L);
        role.setName(name);
        return role;
    }

    private User user(String email, String password, boolean enabled, Role role) {
        User user = new User();
        user.setFullName("Test User");
        user.setEmail(email);
        user.setPassword(password);
        user.setEnabled(enabled);
        user.setRole(role);
        return user;
    }
}
