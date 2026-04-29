package com.docusphere.backend.team.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.docusphere.backend.team.entity.TeamInvitation;

@Repository
public interface TeamInvitationRepository extends JpaRepository<TeamInvitation, UUID> {
    List<TeamInvitation> findByEmail(String email);
    List<TeamInvitation> findAllByTeamId(UUID teamId);
    java.util.Optional<TeamInvitation> findByEmailAndTeamId(String email, UUID teamId);
    void deleteByEmailAndTeamId(String email, UUID teamId);
    void deleteByTeamId(UUID teamId);
}
