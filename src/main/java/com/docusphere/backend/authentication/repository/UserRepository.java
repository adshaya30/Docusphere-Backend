package com.docusphere.backend.authentication.repository;

import com.docusphere.backend.authentication.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.List;


public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email); //to get user's details
    boolean existsByEmail(String email); // For fast duplicate check
    
    @Query("SELECT u.id FROM User u WHERE u.role.name = 'ROLE_ADMIN'")
    List<Long> findAllAdminUserIds();
}
