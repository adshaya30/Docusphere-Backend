package com.docusphere.backend.user.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.authentication.service.EmailService;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.document.storage.FileStorageService;
import com.docusphere.backend.documentStar.repository.DocumentStarRepository;
import com.docusphere.backend.team.dto.AddMemberRequest;
import com.docusphere.backend.team.dto.TeamDto;
import com.docusphere.backend.team.dto.TeamMemberDto;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.entity.TeamInvitation;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.repository.TeamInvitationRepository;
import com.docusphere.backend.team.repository.TeamMemberRepository;
import com.docusphere.backend.team.repository.TeamRepository;
import com.docusphere.backend.team.repository.UserActivityRepository;
import com.docusphere.backend.team.service.TeamService;

import jakarta.mail.MessagingException;
import jakarta.persistence.EntityNotFoundException;

/**
 * Partner's service for leader/member/manager operations.
 * Delegates to shared TeamService for queries.
 * Owns: create team, delete team, add member (by leader).
 */
@Service
public class UserTeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInvitationRepository teamInvitationRepository;
    private final TeamService teamService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final UserActivityRepository userActivityRepository;
    private final DocumentStarRepository documentStarRepository;

    public UserTeamService(TeamRepository teamRepository,
                           TeamMemberRepository teamMemberRepository,
                           TeamInvitationRepository teamInvitationRepository,
                           TeamService teamService,
                           UserRepository userRepository,
                           EmailService emailService,
                           DocumentRepository documentRepository,
                           FileStorageService fileStorageService,
                           UserActivityRepository userActivityRepository,
                           DocumentStarRepository documentStarRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamInvitationRepository = teamInvitationRepository;
        this.teamService = teamService;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.documentRepository = documentRepository;
        this.fileStorageService = fileStorageService;
        this.userActivityRepository = userActivityRepository;
        this.documentStarRepository = documentStarRepository;
    }

    /**
     * Leader creates a team — automatically becomes LEADER.
     * @param name        team name
     * @param description optional description
     * @param leaderId    userId from JWT
     */
    @Transactional
    public TeamDto createTeam(String name, String description, List<AddMemberRequest> initialMembers, Long leaderId) {
        User user = userRepository.findById(leaderId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + leaderId));

        Team team = new Team();
        team.setTeamName(name);
        team.setDescription(description);
        team.setMemberCount(1);
        team.setDocumentCount(0);
        Team savedTeam = teamRepository.save(team);

        TeamMember member = new TeamMember();
        member.setTeam(savedTeam);
        member.setUserId(user.getId());
        member.setFullName(user.getFullName());
        member.setRole(TeamRole.LEADER);
        teamMemberRepository.save(member);

        // Process initial members if any
        if (initialMembers != null) {
            for (AddMemberRequest memberReq : initialMembers) {
                try {
                    addMemberInternal(savedTeam, memberReq, user.getFullName());
                } catch (Exception e) {
                    // Log error but continue for other members
                }
            }
        }

        return teamService.toDto(savedTeam);
    }

    /**
     * Leader deletes a team — removes team + all memberships (cascade).
     */
    @Transactional
    public void deleteTeam(UUID teamId, Long requesterId) {
        if (!teamService.isUserRoleInTeam(requesterId, teamId, TeamRole.LEADER)) {
            throw new IllegalStateException("Only the LEADER can delete the team");
        }

        // 1. Cleanup document-related records
        List<Document> teamDocuments = documentRepository.findAllByTeamId(teamId);
        for (Document document : teamDocuments) {
        
            try {
                fileStorageService.deleteFile(document.getStorageKey());
            } catch (Exception ignored) {
                // Keep deleting even if one storage object cannot be removed.
            }
        }
        documentRepository.deleteAll(teamDocuments);

        // 2. Cleanup team-related records
        userActivityRepository.deleteByTeamId(teamId);
        teamInvitationRepository.deleteByTeamId(teamId);
        teamMemberRepository.deleteByTeamId(teamId);
        
        // 3. Delete the team itself
        teamRepository.deleteById(teamId);
    }

    /**
     * Leader adds a member to their team.
     * Role allowed: MEMBER or MANAGER only.
     */
    @Transactional
    public TeamMemberDto addMember(UUID teamId, AddMemberRequest request, Long requesterId) {
        if (!teamService.isUserRoleInTeam(requesterId, teamId, TeamRole.LEADER)) {
            throw new IllegalStateException("Only the LEADER can add members");
        }

        if (TeamRole.LEADER.name().equals(request.getRole())) {
            throw new IllegalStateException("Cannot add another LEADER to the team");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new EntityNotFoundException("Team not found: " + teamId));

        User inviter = userRepository.findById(requesterId)
                .orElseThrow(() -> new EntityNotFoundException("Inviter not found"));

        return addMemberInternal(team, request, inviter.getFullName());
    }

    private TeamMemberDto addMemberInternal(Team team, AddMemberRequest request, String inviterName) {
        if (request.getUserId() != null) {
            User user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + request.getUserId()));

            if (teamMemberRepository.existsByUserIdAndTeamId(user.getId(), team.getId())) {
                throw new IllegalStateException("User is already in the team");
            }

            TeamMember member = new TeamMember();
            member.setTeam(team);
            member.setUserId(user.getId());
            member.setFullName(user.getFullName());
            member.setRole(TeamRole.valueOf(request.getRole()));
            TeamMember saved = teamMemberRepository.save(member);
            teamRepository.incrementMemberCount(team.getId());
            return teamService.toMemberDto(saved);
        } else if (request.getEmail() != null) {
            String email = request.getEmail().toLowerCase();
            TeamRole role = TeamRole.valueOf(request.getRole());

            return userRepository.findByEmail(email)
                    .map(user -> {
                        if (teamMemberRepository.existsByUserIdAndTeamId(user.getId(), team.getId())) {
                            throw new IllegalStateException("User is already in the team");
                        }
                        TeamMember member = new TeamMember();
                        member.setTeam(team);
                        member.setUserId(user.getId());
                        member.setFullName(user.getFullName());
                        member.setRole(role);
                        TeamMember saved = teamMemberRepository.save(member);
                        teamRepository.incrementMemberCount(team.getId());
                        return teamService.toMemberDto(saved);
                    })
                    .orElseGet(() -> {
                        // Create invitation
                        TeamInvitation invitation = new TeamInvitation();
                        invitation.setEmail(email);
                        invitation.setTeamId(team.getId());
                        invitation.setRole(role);
                        teamInvitationRepository.save(invitation);

                        // Send email
                        try {
                            emailService.sendTeamInvitationEmail(email, team.getTeamName(), inviterName);
                        } catch (MessagingException e) {
                            // Non-fatal, invitation is still saved
                        }

                        // Return a placeholder or null as they aren't a member yet
                        TeamMemberDto placeholder = new TeamMemberDto();
                        placeholder.setEmail(email);
                        placeholder.setRole(role.name());
                        placeholder.setTeamId(team.getId());
                        placeholder.setFullName("Pending Invitation");
                        return placeholder;
                    });
        } else {
            throw new IllegalArgumentException("Either userId or email must be provided");
        }
    }

    @Transactional
    public void processPendingInvitations(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        List<TeamInvitation> invitations = teamInvitationRepository.findByEmail(user.getEmail().toLowerCase());

        for (TeamInvitation invitation : invitations) {
            teamRepository.findById(invitation.getTeamId()).ifPresent(team -> {
                if (!teamMemberRepository.existsByUserIdAndTeamId(user.getId(), team.getId())) {
                    TeamMember member = new TeamMember();
                    member.setTeam(team);
                    member.setUserId(user.getId());
                    member.setFullName(user.getFullName());
                    member.setRole(invitation.getRole());
                    teamMemberRepository.save(member);
                    teamRepository.incrementMemberCount(team.getId());
                }
            });
            teamInvitationRepository.delete(invitation);
        }
    }
    @Transactional
    public void removeMember(UUID teamId, Long memberUserId, Long requesterId) {
        if (!teamService.isUserRoleInTeam(requesterId, teamId, TeamRole.LEADER)) {
            throw new IllegalStateException("Only the LEADER can remove members");
        }

        TeamMember membership = teamMemberRepository.findByUserIdAndTeamId(memberUserId, teamId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found in team"));

        if (membership.getRole() == TeamRole.LEADER) {
            throw new IllegalStateException("Cannot remove the LEADER. Transfer leadership first.");
        }

        teamMemberRepository.delete(membership);
        teamRepository.decrementMemberCount(teamId);
    }

    @Transactional
    public void updateMemberRole(UUID teamId, Long memberUserId, String newRole, Long requesterId) {
        if (!teamService.isUserRoleInTeam(requesterId, teamId, TeamRole.LEADER)) {
            throw new IllegalStateException("Only the LEADER can update roles");
        }

        TeamMember membership = teamMemberRepository.findByUserIdAndTeamId(memberUserId, teamId)
                .orElseThrow(() -> new EntityNotFoundException("Member not found in team"));

        if (membership.getRole() == TeamRole.LEADER) {
            throw new IllegalStateException("Cannot update the LEADER's role directly.");
        }

        TeamRole role = TeamRole.valueOf(newRole);
        if (role == TeamRole.LEADER) {
            throw new IllegalStateException("Use transfer leader endpoint to change LEADER");
        }

        membership.setRole(role);
        teamMemberRepository.save(membership);
    }
}
