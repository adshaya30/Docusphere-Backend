package com.docusphere.backend.admin.team.service;

import com.docusphere.backend.admin.team.dto.AdminMemberView;
import com.docusphere.backend.Common.config.AppConfig;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.EmailService;
import com.docusphere.backend.team.dto.AddMemberRequest;
import com.docusphere.backend.team.dto.TransferLeaderRequest;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.entity.TeamInvitation;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.repository.TeamInvitationRepository;
import com.docusphere.backend.team.repository.TeamMemberRepository;
import com.docusphere.backend.team.repository.TeamRepository;
import com.docusphere.backend.notification.service.NotificationHelper;
import com.docusphere.backend.notification.util.NotificationUserIds;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;



import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminTeamMemberService {
    private static final String TEAM_INVITE_QUERY = "/?teamInvite=1";

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInvitationRepository teamInvitationRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AppConfig appConfig;
    private final AdminTeamQueryService queryService;
    private final NotificationHelper notificationHelper;


    public AdminTeamMemberService(TeamRepository tr, TeamMemberRepository tmr, TeamInvitationRepository tir, 
                                  UserRepository ur, EmailService es, AppConfig appConfig, AdminTeamQueryService qs,
                                  NotificationHelper notificationHelper) {
        this.teamRepository = tr; this.teamMemberRepository = tmr; this.teamInvitationRepository = tir;
        this.userRepository = ur; this.emailService = es; this.appConfig = appConfig; this.queryService = qs;
        this.notificationHelper = notificationHelper;
    }

    @Transactional
    public AdminMemberView addMember(UUID teamId, AddMemberRequest req) {
        Team team = queryService.assertTeamExists(teamId);
        
        // Retrieve current authenticated admin info
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentEmail = auth != null ? auth.getName() : null;
        User admin = currentEmail != null ? userRepository.findByEmail(currentEmail).orElse(null) : null;
        Long adminId = admin != null ? admin.getId() : null;
        String adminName = admin != null ? admin.getFullName() : "Admin";

        if (req.getUserId() != null) {
            if (teamMemberRepository.existsByUserIdAndTeamId(req.getUserId(), teamId)) throw new IllegalStateException("Already a member");
            User u = userRepository.findById(req.getUserId()).orElseThrow(() -> new EntityNotFoundException("User not found"));
            
            TeamInvitation inv = new TeamInvitation();
            inv.setEmail(u.getEmail().toLowerCase());
            inv.setTeamId(teamId);
            inv.setRole(TeamRole.valueOf(req.getRole()));
            inv.setInviterId(adminId);
            inv.setInviterName(adminName);
            TeamInvitation savedInvite = teamInvitationRepository.save(inv);
            
            try {
                java.util.UUID userUuid = NotificationUserIds.fromUserId(u.getId());
                notificationHelper.notifyTeamInvitation(userUuid, teamId, team.getTeamName(), savedInvite.getId(), adminName);
            } catch (Exception e) {
                // Non-fatal
            }
            return queryService.toAdminMemberViewFromInvitation(savedInvite);
        }

        String email = req.getEmail().toLowerCase();
        return userRepository.findByEmail(email).map(u -> {
            if (teamMemberRepository.existsByUserIdAndTeamId(u.getId(), teamId)) throw new IllegalStateException("Already a member");
            
            TeamInvitation inv = new TeamInvitation();
            inv.setEmail(email);
            inv.setTeamId(teamId);
            inv.setRole(TeamRole.valueOf(req.getRole()));
            inv.setInviterId(adminId);
            inv.setInviterName(adminName);
            TeamInvitation savedInvite = teamInvitationRepository.save(inv);
            
            try {
                java.util.UUID userUuid = NotificationUserIds.fromUserId(u.getId());
                notificationHelper.notifyTeamInvitation(userUuid, teamId, team.getTeamName(), savedInvite.getId(), adminName);
            } catch (Exception e) {
                // Non-fatal
            }
            return queryService.toAdminMemberViewFromInvitation(savedInvite);
        }).orElseGet(() -> {
            TeamInvitation inv = new TeamInvitation();
            inv.setEmail(email);
            inv.setTeamId(teamId);
            inv.setRole(TeamRole.valueOf(req.getRole()));
            inv.setInviterId(adminId);
            inv.setInviterName(adminName);
            TeamInvitation savedInvite = teamInvitationRepository.save(inv);
            try {
                emailService.sendTeamInvitationEmail(email, team.getTeamName(), adminName, appConfig.getFrontendUrl() + TEAM_INVITE_QUERY);
            } catch (Exception ignored) {}
            return queryService.toAdminMemberViewFromInvitation(savedInvite);
        });
    }

    private AdminMemberView saveAndReturn(Team t, Long uid, String name, String role) {
        TeamMember m = new TeamMember(); m.setTeam(t); m.setUserId(uid); m.setFullName(name); m.setRole(TeamRole.valueOf(role));
        TeamMember saved = teamMemberRepository.save(m);
        teamRepository.incrementMemberCount(t.getId());

        try {
            java.util.UUID userUuid = NotificationUserIds.fromUserId(uid);
            notificationHelper.notifyUserAddedToTeam(userUuid, t.getId(), t.getTeamName());
        } catch (Exception e) {
            // Non-fatal
        }

        return queryService.toAdminMemberView(saved);
    }

    @Transactional
    public void deleteMember(UUID teamId, Long userId) {
        TeamMember m = teamMemberRepository.findByUserIdAndTeamId(userId, teamId).orElseThrow(() -> new EntityNotFoundException("Not a member"));
        if (m.getRole() == TeamRole.LEADER) throw new IllegalStateException("Cannot delete LEADER");
        
        Team team = m.getTeam();
        String teamName = team != null ? team.getTeamName() : "Team";

        teamMemberRepository.delete(m);
        teamRepository.decrementMemberCount(teamId);

        try {
            java.util.UUID userUuid = NotificationUserIds.fromUserId(userId);
            notificationHelper.notifyUserRemovedFromTeam(userUuid, teamId, teamName);
        } catch (Exception e) {
            // Non-fatal
        }
    }

    @Transactional
    public void updateMemberRole(UUID teamId, Long userId, String newRole) {
        TeamMember m = teamMemberRepository.findByUserIdAndTeamId(userId, teamId).orElseThrow(() -> new EntityNotFoundException("Not a member"));
        TeamRole role = TeamRole.valueOf(newRole);
        if (m.getRole() == TeamRole.LEADER || role == TeamRole.LEADER) throw new IllegalStateException("Use transfer-leader for LEADER roles");
        m.setRole(role);
        teamMemberRepository.save(m);

        // Notify user that their role in a team has been updated
        try {
            java.util.UUID userUuid = NotificationUserIds.fromUserId(userId);
            notificationHelper.notifyUserRoleUpdated(userUuid, teamId, m.getTeam().getTeamName(), role.name());
        } catch (Exception e) {
            // Non-fatal
        }
    }

    @Transactional
    public void transferLeader(UUID teamId, TransferLeaderRequest req) {
        if (req.getNewLeaderId() == null) throw new IllegalArgumentException("Target leader ID cannot be null");

        TeamMember curr = teamMemberRepository.findLeaderByTeamId(teamId).orElseThrow(() -> new EntityNotFoundException("No leader"));
        TeamMember next = teamMemberRepository.findByUserIdAndTeamId(req.getNewLeaderId(), teamId)
                .orElseThrow(() -> new EntityNotFoundException("Target member must be a 'Joined' user. Pending invitations cannot be leaders."));

        if (next.getRole() == TeamRole.LEADER) throw new IllegalStateException("Already leader");
        curr.setRole(TeamRole.MEMBER); next.setRole(TeamRole.LEADER);
        teamMemberRepository.save(curr); teamMemberRepository.save(next);

        // Notify the former leader that their role is now MEMBER
        try {
            java.util.UUID oldLeaderUuid = NotificationUserIds.fromUserId(curr.getUserId());
            notificationHelper.notifyUserRoleUpdated(oldLeaderUuid, teamId, curr.getTeam().getTeamName(), "MEMBER");
        } catch (Exception e) {
            // Non-fatal
        }

        // Notify the new leader that their role is now LEADER
        try {
            java.util.UUID newLeaderUuid = NotificationUserIds.fromUserId(req.getNewLeaderId());
            notificationHelper.notifyUserRoleUpdated(newLeaderUuid, teamId, next.getTeam().getTeamName(), "LEADER");
        } catch (Exception e) {
            // Non-fatal
        }
    }
}
