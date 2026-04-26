package com.docusphere.backend.admin.dashboard.repository;

import com.docusphere.backend.authentication.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminUserRepository extends JpaRepository<User, Long> {

    // Total user count for the current month
    @Query(value = """
                SELECT COUNT(*)
                FROM users
                WHERE created_at >= date_trunc('month', CURRENT_DATE)
            """, nativeQuery = true)
    Long getCurrentMonthUserCount();

    // Total user count for the previous month
    @Query(value = """
            SELECT COUNT(*)
            FROM users
            WHERE created_at >= date_trunc('month', CURRENT_DATE - INTERVAL '1 month')
              AND created_at < date_trunc('month', CURRENT_DATE)
            """, nativeQuery = true)
    Long getPreviousMonthUserCount();

    // Count active sessions from Supabase auth schema
    @Query(value = "SELECT COUNT(*) FROM auth.sessions", nativeQuery = true)
    Long countActiveSessions();

}