package com.docusphere.backend.admin.dashboard.repository;

import com.docusphere.backend.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AdminTeamRepository extends JpaRepository<Team, UUID> {

    //  Teams created in current month
    @Query(value = """
        SELECT COUNT(*)
        FROM team
        WHERE DATE_TRUNC('month', created_at) = DATE_TRUNC('month', CURRENT_DATE)
    """, nativeQuery = true)
    Long getCurrentMonthTeamCount();

    //  Teams created in previous month
    @Query(value = """
        SELECT COUNT(*)
        FROM team
        WHERE DATE_TRUNC('month', created_at) =
              DATE_TRUNC('month', CURRENT_DATE - INTERVAL '1 month')
    """, nativeQuery = true)
    Long getPreviousMonthTeamCount();
}
