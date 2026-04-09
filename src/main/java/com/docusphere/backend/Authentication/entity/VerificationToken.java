package com.docusphere.backend.Authentication.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "Verification_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class VerificationToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @OneToOne(targetEntity = User.class, fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    // Check if the token is expired
    public boolean isExpired() {
        return expiryDate.isBefore(LocalDateTime.now());
    }
}
