package com.docusphere.backend.admin.team.service;

import com.docusphere.backend.admin.team.dto.AdminCreateTeamRequest;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.documentShare.repository.DocumentShareRepository;
import com.docusphere.backend.documentStar.repository.DocumentStarRepository;
import com.docusphere.backend.team.dto.TeamDto;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.entity.TeamInvitation;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.repository.TeamInvitationRepository;
import com.docusphere.backend.team.repository.TeamMemberRepository;
import com.docusphere.backend.team.repository.TeamRepository;
import com.docusphere.backend.team.repository.UserActivityRepository;
import com.docusphere.backend.team.service.TeamService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;


@Service
public class AdminTeamCoreService {
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInvitationRepository teamInvitationRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentStarRepository documentStarRepository;
    private final DocumentShareRepository documentShareRepository;
    private final UserActivityRepository userActivityRepository;
    private final TeamService teamService;

    public AdminTeamCoreService(TeamRepository tr, TeamMemberRepository tmr, TeamInvitationRepository tir, 
                                UserRepository ur, DocumentRepository dr, DocumentStarRepository dsr, 
                                DocumentShareRepository dsher, UserActivityRepository uar, TeamService ts) {
        this.teamRepository = tr; this.teamMemberRepository = tmr; this.teamInvitationRepository = tir;
        this.userRepository = ur; this.documentRepository = dr; this.documentStarRepository = dsr; 
        this.documentShareRepository = dsher; this.userActivityRepository = uar; this.teamService = ts;
    }

    @Transactional
    public TeamDto createTeam(AdminCreateTeamRequest req) {
        User leader = null;

        // 1. Try to find if a leader was explicitly assigned in the additional members list
        if (req.getAdditionalMembers() != null) {
            for (AdminCreateTeamRequest.AdditionalMember am : req.getAdditionalMembers()) {
                if ("LEADER".equalsIgnoreCase(am.getRole())) {
                    if (am.getUserId() != null) {
                        leader = userRepository.findById(am.getUserId()).orElse(null);
                    } else if (am.getEmail() != null && !am.getEmail().isBlank()) {
                        leader = userRepository.findByEmail(am.getEmail().toLowerCase()).orElse(null);
                    }
                    if (leader != null) break;
                }
            }
        }

        // 2. Fallback to the provided leaderId (usually the admin making the request)
        if (leader == null && req.getLeaderId() != null) {
            leader = userRepository.findById(req.getLeaderId()).orElse(null);
        }

        if (leader == null) {
            throw new IllegalArgumentException("Team creation failed: You must assign a 'Leader' from the member list. Administrators are not automatically added to teams.");
        }

        Team team = new Team();
        team.setTeamName(req.getName());
        team.setDescription(req.getDescription());
        Team saved = teamRepository.save(team);

        saveMember(saved, leader.getId(), leader.getFullName(), TeamRole.LEADER);

        if (req.getAdditionalMembers() != null) {
            final User finalLeader = leader;
            req.getAdditionalMembers().stream()
                .filter(am -> !isLeader(am, finalLeader))
                .forEach(am -> addMemberInternal(saved, am));
        }

        return teamService.toDto(teamRepository.save(saved));
    }

    private boolean isLeader(AdminCreateTeamRequest.AdditionalMember am, User leader) {
        return (am.getUserId() != null && am.getUserId().equals(leader.getId())) || 
               (am.getEmail() != null && am.getEmail().equalsIgnoreCase(leader.getEmail()));
    }

    private void addMemberInternal(Team team, AdminCreateTeamRequest.AdditionalMember am) {
        TeamRole role = am.getRole() != null ? TeamRole.valueOf(am.getRole()) : TeamRole.MEMBER;
        if (am.getUserId() != null) {
            userRepository.findById(am.getUserId()).ifPresent(u -> saveMember(team, u.getId(), u.getFullName(), role));
        } else if (am.getEmail() != null && !am.getEmail().isBlank()) {
            userRepository.findByEmail(am.getEmail().toLowerCase()).ifPresentOrElse(
                u -> saveMember(team, u.getId(), u.getFullName(), role),
                () -> {
                    TeamInvitation inv = new TeamInvitation(); inv.setEmail(am.getEmail().toLowerCase());
                    inv.setTeamId(team.getId()); inv.setRole(role);
                    teamInvitationRepository.save(inv);
                }
            );
        }
    }

    private void saveMember(Team team, Long userId, String name, TeamRole role) {
        if (!teamMemberRepository.existsByUserIdAndTeamId(userId, team.getId())) {
            TeamMember m = new TeamMember(); m.setTeam(team); m.setUserId(userId);
            m.setFullName(name); m.setRole(role);
            teamMemberRepository.save(m);
            team.setMemberCount(team.getMemberCount() + 1);
        }
    }

    @Transactional
    public void deleteTeam(UUID teamId) {
        List<UUID> docIds = documentRepository.findAllByTeamId(teamId).stream().map(Document::getId).toList();
        if (!docIds.isEmpty()) {
            documentStarRepository.deleteByDocumentIdIn(docIds);
            documentShareRepository.deleteByDocumentIdIn(docIds);
            documentRepository.deleteByTeamId(teamId);
        }
        userActivityRepository.deleteByTeamId(teamId);
        teamMemberRepository.deleteByTeamId(teamId);
        teamInvitationRepository.deleteByTeamId(teamId);
        teamRepository.deleteById(teamId);
    }
}

