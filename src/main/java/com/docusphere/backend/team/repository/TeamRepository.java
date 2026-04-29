package com.docusphere.backend.team.repository;


import com.docusphere.backend.team.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {

    Optional<Team> findByTeamName(String teamName);

    boolean existsByTeamName(String teamName);

    @Modifying
    @Query("UPDATE Team t SET t.documentCount = t.documentCount + 1 WHERE t.id = :id")
    void incrementDocumentCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Team t SET t.documentCount = CASE WHEN t.documentCount > 0 THEN t.documentCount - 1 ELSE 0 END WHERE t.id = :id")
    void decrementDocumentCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Team t SET t.memberCount = t.memberCount + 1 WHERE t.id = :id")
    void incrementMemberCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Team t SET t.memberCount = CASE WHEN t.memberCount > 0 THEN t.memberCount - 1 ELSE 0 END WHERE t.id = :id")
    void decrementMemberCount(@Param("id") UUID id);
}