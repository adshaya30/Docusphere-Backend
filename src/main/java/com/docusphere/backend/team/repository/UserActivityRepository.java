package com.docusphere.backend.team.repository;

import com.docusphere.backend.team.entity.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, UUID> {
    boolean existsByUserIdAndTeamIdAndOccurredAtAfter(Long userId, UUID teamId, LocalDateTime time);
    void deleteByTeamId(UUID teamId);
}
