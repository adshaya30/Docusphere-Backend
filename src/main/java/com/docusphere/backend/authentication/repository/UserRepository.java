package com.docusphere.backend.authentication.repository;

import com.docusphere.backend.authentication.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;


public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email); //to get user's details
    boolean existsByEmail(String email); // For fast duplicate check
}
