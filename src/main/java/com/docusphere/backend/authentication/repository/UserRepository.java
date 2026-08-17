package com.docusphere.backend.authentication.repository;

import com.docusphere.backend.authentication.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;


public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email); //to get user's details
    boolean existsByEmail(String email); // For fast duplicate check

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.accountLocked = false, u.lockedUntil = null WHERE u.accountLocked = true AND u.lockedUntil < :now")
    int unlockExpiredAccounts(@Param("now") LocalDateTime now);
}
