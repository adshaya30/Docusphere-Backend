package com.docusphere.backend.admin.team.service;


import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.entity.TeamInvitation;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.repository.TeamInvitationRepository;
import com.docusphere.backend.team.repository.TeamMemberRepository;
import com.docusphere.backend.team.repository.TeamRepository;
import com.docusphere.backend.document.repository.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class AdminTeamMergeService {
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInvitationRepository teamInvitationRepository;
    private final DocumentRepository documentRepository;
    private final AdminTeamQueryService queryService;

    public AdminTeamMergeService(TeamRepository tr, TeamMemberRepository tmr, TeamInvitationRepository tir, 
                                 DocumentRepository dr, AdminTeamQueryService qs) {
        this.teamRepository = tr; 
        this.teamMemberRepository = tmr; 
        this.teamInvitationRepository = tir;
        this.documentRepository = dr; 
        this.queryService = qs;
    }

    @Transactional
    public Team mergeTeams(UUID sourceId, UUID targetId, String name, Long leaderId, boolean moveDocuments) {
        log.info("Starting merge: source={}, target={}, newName={}, moveDocs={}", sourceId, targetId, name, moveDocuments);

        if (sourceId.equals(targetId)) throw new IllegalArgumentException("Cannot merge same team");

        if (teamRepository.existsByTeamName(name)) {
            throw new IllegalArgumentException("A team with the name '" + name + "' already exists. Please choose a unique name.");
        }

        Team src = queryService.assertTeamExists(sourceId);
        Team tgt = queryService.assertTeamExists(targetId);

        Team mTeam = new Team();
        mTeam.setTeamName(name);
        mTeam.setDescription("Merged from " + src.getTeamName() + " and " + tgt.getTeamName());
        Team merged = teamRepository.save(mTeam);
        log.info("Created merged team entity with ID: {}", merged.getId());

        Set<Long> users = new HashSet<>();
        List.of(sourceId, targetId).forEach(id -> {
            log.info("Processing source team: {}", id);
            // Move Members
            teamMemberRepository.findAllByTeamId(id).forEach(old -> {
                if (old.getUserId() != null && users.add(old.getUserId())) {
                    saveMember(merged, old);
                }
            });
            log.info("Finished moving members for team: {}", id);

            // Move Invitations
            teamInvitationRepository.findAllByTeamId(id).forEach(oldInvite -> {
                if (teamInvitationRepository.findByEmailAndTeamId(oldInvite.getEmail(), merged.getId()).isEmpty()) {
                    saveInvite(merged.getId(), oldInvite);
                }
            });

            // Optional: Move Documents
            if (moveDocuments) {
                log.info("Moving documents for team: {}", id);
                documentRepository.updateAllTeamId(id, merged.getId());
                resetTeam(id);
            }
        });

        // Ensure members are in DB before promotion
        log.info("Flushing member changes to DB...");
        teamMemberRepository.flush();

        try {
            log.info("Promoting leader with ID: {}", leaderId);
            promoteLeader(leaderId, merged.getId());
        } catch (Exception e) {
            log.error("Failed to promote leader: {}", e.getMessage());
            throw new IllegalArgumentException("Could not assign the selected leader. Please ensure the user is part of the merged teams.");
        }

        merged.setMemberCount(users.size());
        merged.setDocumentCount(documentRepository.findAllByTeamId(merged.getId()).size());

        log.info("Merge completed successfully for team: {}", name);
        return teamRepository.save(merged);
    }

    private void promoteLeader(Long lid, UUID tid) {
        TeamMember m = teamMemberRepository.findByUserIdAndTeamId(lid, tid)
                .orElseThrow(() -> new EntityNotFoundException("Leader not found"));
        m.setRole(TeamRole.LEADER);
        teamMemberRepository.save(m);
    }

    private void saveMember(Team t, TeamMember old) {
        TeamMember m = new TeamMember();
        m.setTeam(t);
        m.setUserId(old.getUserId());
        m.setFullName(old.getFullName());
        m.setRole(TeamRole.MEMBER);
        teamMemberRepository.save(m);
    }

    private void saveInvite(UUID tid, TeamInvitation old) {
        TeamInvitation i = new TeamInvitation();
        i.setEmail(old.getEmail());
        i.setTeamId(tid);
        i.setRole(old.getRole());
        teamInvitationRepository.save(i);
    }

    private void resetTeam(UUID id) {
        teamRepository.findById(id).ifPresent(t -> {
            t.setDocumentCount(0);
            teamRepository.save(t);
        });
    }
}
