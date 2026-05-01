package com.docusphere.backend.team.repository;

import com.docusphere.backend.team.entity.UserActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {
    boolean existsByUserIdAndTeamIdAndOccurredAtAfter(Long userId, UUID teamId, LocalDateTime time);

    @Modifying
    void deleteByTeamId(UUID teamId);
}
