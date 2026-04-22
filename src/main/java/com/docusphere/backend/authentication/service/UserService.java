package com.docusphere.backend.authentication.service;

import com.docusphere.backend.Common.config.AdminConfig;
import com.docusphere.backend.Common.config.AppConfig;
import com.docusphere.backend.authentication.dto.SignUpRequest;
import com.docusphere.backend.authentication.dto.ResetPasswordRequest;
import com.docusphere.backend.authentication.entity.Role;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.entity.VerificationToken;
import com.docusphere.backend.authentication.entity.PasswordResetToken;
import com.docusphere.backend.Common.exception.*;
import com.docusphere.backend.Common.exception.EmailNotVerifiedException;
import com.docusphere.backend.authentication.repository.PasswordResetTokenRepository;
import com.docusphere.backend.authentication.repository.RoleRepository;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.repository.VerificationTokenRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminConfig adminConfig;
    private final AppConfig appConfig;
    private final EmailService emailService;

    @Transactional
    public void signUp(SignUpRequest dto) {

        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new InvalidPasswordException("Passwords do not match");
        }

        if (userRepository.existsByEmail(dto.getEmail().toLowerCase())) {
            throw new EmailAlreadyExistsException("Email already registered");
        }


        String roleName = isAdminEmail(dto.getEmail()) ? "ROLE_ADMIN" : "ROLE_USER";
        Role role = getOrCreateRole(roleName);


        User user = new User();
        user.setFullName(dto.getFullName());
        user.setEmail(dto.getEmail().toLowerCase());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setEnabled(false);
        user.setRole(role);

        user = userRepository.save(user);

        String token = UUID.randomUUID().toString();
        VerificationToken verificationToken = new VerificationToken();
        verificationToken.setToken(token);
        verificationToken.setUser(user);
        verificationToken.setExpiryDate(LocalDateTime.now().plusHours(24));
        tokenRepository.save(verificationToken);

        // Send verification email asynchronously
        String verificationLink = appConfig.getFrontendUrl() + "/verify-email?token=" + token;
        sendVerificationEmailAsync(user.getEmail(), verificationLink);
    }

    // Send email asynchronously without blocking the response
    private void sendVerificationEmailAsync(String email, String verificationLink) {
        new Thread(() -> {
            try {
                emailService.sendVerificationEmail(email, verificationLink);
            } catch (MessagingException e) {
                log.error("Failed to send verification email to: {}", email, e);
            }
        }).start();
    }

    // This ensures roles are saved even if the main signup transaction fails
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Role getOrCreateRole(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    Role newRole = new Role();
                    newRole.setName(roleName);
                    return roleRepository.save(newRole);
                });
    }

    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with email: " + email));
    }

    @Transactional
    public User verifyEmail(String token) {

        VerificationToken vt = tokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidTokenException("Invalid token"));

        if (vt.isExpired()) {
            throw new TokenExpiredException("Token has expired");
        }

        User user = vt.getUser();
        user.setEnabled(true);
        userRepository.save(user);
        tokenRepository.delete(vt);
        return user;
    }

    @Transactional
    public void resendVerificationEmail(String email) {
        try {
            User user = findByEmail(email);
            String token = UUID.randomUUID().toString();
            java.util.Optional<VerificationToken> existingToken = tokenRepository.findByUser(user);

            VerificationToken verificationToken;
            if (existingToken.isPresent()) {
                verificationToken = existingToken.get();
                verificationToken.setToken(token);
                verificationToken.setExpiryDate(LocalDateTime.now().plusHours(24));

            } else {
                verificationToken = new VerificationToken();
                verificationToken.setToken(token);
                verificationToken.setUser(user);
                verificationToken.setExpiryDate(LocalDateTime.now().plusHours(24));

            }

            tokenRepository.save(verificationToken);
            String verificationLink = appConfig.getFrontendUrl() + "/verify-email?token=" + token;
            sendVerificationEmailAsync(user.getEmail(), verificationLink);
        } catch (Exception e) {
            log.error("Error during email resend process: ", e);
            throw e;
        }
    }

    private boolean isAdminEmail(String email) {
        return adminConfig.getAdminEmails().stream()
                .anyMatch(admin -> admin.equalsIgnoreCase(email));
    }

    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UserNotFoundException("No account found with this email"));

        if (!user.isEnabled()) {
            throw new EmailNotVerifiedException("Please verify your email first before resetting password");
        }

        passwordResetTokenRepository.deleteByUserId(user.getId());

        // Create a fresh new token
        String token = UUID.randomUUID().toString();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(30)); // 30 minutes expiry

        passwordResetTokenRepository.save(resetToken);

        String resetLink = appConfig.getFrontendUrl() + "/reset-password?token=" + token;

        // Send email asynchronously (non-blocking)
        sendPasswordResetEmailAsync(user.getEmail(), resetLink);
    }

    // Send password reset email asynchronously without blocking the response
    private void sendPasswordResetEmailAsync(String email, String resetLink) {
        new Thread(() -> {
            try {
                emailService.sendPasswordResetEmail(email, resetLink);
            } catch (MessagingException e) {
                log.error("Failed to send password reset email to: {}", email, e);
            }
        }).start();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new InvalidPasswordException("Passwords do not match");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> {
                    return new InvalidTokenException("Invalid or expired reset link");
                });

        if (resetToken.isExpired()) {
            throw new TokenExpiredException("This reset link has expired. Please request a new one.");
        }

        // Get user and check if new password is different from current password
        User user = resetToken.getUser();

        if (user == null) {
            throw new UserNotFoundException("User associated with reset token not found");
        }

        // Check if new password matches current password
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new InvalidPasswordException("New password must be different from your current password");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        passwordResetTokenRepository.delete(resetToken);
    }
}



