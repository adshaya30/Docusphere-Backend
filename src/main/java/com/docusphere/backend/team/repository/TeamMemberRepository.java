package com.docusphere.backend.team.repository;



import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.entity.TeamRole;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    // Find a specific user's membership in a team
    Optional<TeamMember> findByUserIdAndTeamId(Long userId, UUID teamId);

    // All members of a team
    List<TeamMember> findAllByTeamId(UUID teamId);

    // All members of a team with a specific role
    List<TeamMember> findAllByTeamIdAndRole(UUID teamId, TeamRole role);

    // All teams a user belongs to
    List<TeamMember> findAllByUserId(Long userId);

    // Check if a user holds a specific role in a team
    boolean existsByUserIdAndTeamIdAndRole(Long userId, UUID teamId, TeamRole role);

    // Check if a user is in a team (any role)
    boolean existsByUserIdAndTeamId(Long userId, UUID teamId);

    void deleteByTeamId(UUID teamId);

    // Find the current leader of a team
    @Query("SELECT tm FROM TeamMember tm WHERE tm.team.id = :teamId AND tm.role = com.docusphere.backend.team.entity.TeamRole.LEADER")
    Optional<TeamMember> findLeaderByTeamId(@Param("teamId") UUID teamId);

    // Count members by role in a team
    long countByTeamIdAndRole(UUID teamId, TeamRole role);

    long countByTeamId(UUID teamId);
}