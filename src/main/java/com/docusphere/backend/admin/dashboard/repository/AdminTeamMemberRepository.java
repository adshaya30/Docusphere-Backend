package com.docusphere.backend.admin.dashboard.repository;

import com.docusphere.backend.team.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminTeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    // Count active members across all teams
    @Query(value = "SELECT COUNT(DISTINCT user_id) FROM team_members WHERE active = true", nativeQuery = true)
    Long countDistinctActiveUsers();

    // Top teams by total members and activity percentage using native query
    @Query(value = """
            SELECT t.team_name, COUNT(tm.id) as member_count,
                   COALESCE(SUM(CASE WHEN tm.active IS TRUE THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(tm.id), 0), 0) as activity_percent
            FROM team t
            LEFT JOIN team_members tm ON t.id = tm.team_id
            GROUP BY t.id, t.team_name
            ORDER BY activity_percent DESC, member_count DESC
            LIMIT 5
            """, nativeQuery = true)
    List<Object[]> getTopTeamsByMembers();

}
