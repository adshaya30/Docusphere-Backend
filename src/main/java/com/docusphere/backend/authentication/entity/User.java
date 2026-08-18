package com.docusphere.backend.authentication.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    // Account must be verified before accessing the dashboard
    @Column(nullable = false)
    private boolean enabled = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "profile_picture_url", length = 1000)
    private String profilePictureUrl;

    // Account Lockout Fields
    @Column(name = "failed_login_attempts", nullable = false)
    private Integer failedLoginAttempts = 0;

    @Column(name = "account_locked", nullable = false)
    private Boolean accountLocked = false;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    public boolean isAccountLocked() {
        if (accountLocked == null || !accountLocked) {
            return false;
        }

        if (lockedUntil != null && LocalDateTime.now().isAfter(lockedUntil)) {
            accountLocked = false;
            failedLoginAttempts = 0;
            lockedUntil = null;
            return false;
        }

        return true;
    }
}