package com.docusphere.backend.authentication.service;

import com.docusphere.backend.Common.config.AdminConfig;
import com.docusphere.backend.Common.config.AppConfig;
import com.docusphere.backend.authentication.dto.SignUpRequest;
import com.docusphere.backend.authentication.entity.Role;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.entity.VerificationToken;
import com.docusphere.backend.Common.exception.*;
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
        sendVerificationEmailAsync(java.util.Objects.requireNonNull(user.getEmail()), verificationLink);
    }

    // Send email asynchronously without blocking the response
    private void sendVerificationEmailAsync(@org.springframework.lang.NonNull String email, @org.springframework.lang.NonNull String verificationLink) {
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
                .orElseThrow(() -> {
                    return new InvalidTokenException("Invalid token");
                });

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
            sendVerificationEmailAsync(java.util.Objects.requireNonNull(user.getEmail()), verificationLink);
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }
    private boolean isAdminEmail(String email) {
        return adminConfig.getAdminEmails().stream()
                .anyMatch(admin -> admin.equalsIgnoreCase(email));
    }
}

