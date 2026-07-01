package com.docusphere.backend.team.service;

import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.team.dto.TeamDto;
import com.docusphere.backend.team.dto.TeamMemberDto;
import com.docusphere.backend.team.dto.UserStatusDto;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.repository.TeamMemberRepository;
import com.docusphere.backend.team.repository.TeamRepository;
import com.docusphere.backend.team.repository.UserActivityRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core reusable team logic.
 * No admin-specific rules here — those live in AdminTeamService.
 * No leader-specific rules here — those live in UserTeamService (partner).
 */
@Service
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final UserActivityRepository userActivityRepository;

    public TeamService(TeamRepository teamRepository,
                       TeamMemberRepository teamMemberRepository,
                       DocumentRepository documentRepository,
                       UserRepository userRepository,
                       UserActivityRepository userActivityRepository) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.userActivityRepository = userActivityRepository;
    }

    //Finders

    public Optional<Team> findTeamById(UUID teamId) {
        return teamRepository.findById(teamId);
    }

    public List<TeamDto> getAllTeams() {
        return teamRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<TeamMemberDto> getMembersOfTeam(UUID teamId) {
        return teamMemberRepository.findAllByTeamId(teamId)
                .stream()
                .map(this::toMemberDto)
                .collect(Collectors.toList());
    }

    public List<TeamMemberDto> getMembersByRole(UUID teamId, TeamRole role) {
        return teamMemberRepository.findAllByTeamIdAndRole(teamId, role)
                .stream()
                .map(this::toMemberDto)
                .collect(Collectors.toList());
    }

    // Membership checks

    public boolean isUserInTeam(Long userId, UUID teamId) {
        return teamMemberRepository.existsByUserIdAndTeamId(userId, teamId);
    }

    public boolean isUserRoleInTeam(Long userId, UUID teamId, TeamRole role) {
        return teamMemberRepository.existsByUserIdAndTeamIdAndRole(
                userId, teamId, role);
    }

    public Optional<TeamMember> findMembership(Long userId, UUID teamId) {
        return teamMemberRepository.findByUserIdAndTeamId(userId, teamId);
    }

    public Optional<TeamMember> findMembership(Long userId, UUID teamId, TeamRole role) {
        return teamMemberRepository.findByUserIdAndTeamId(userId, teamId)
                .filter(m -> m.getRole().equals(role));
    }

    public Optional<TeamMember> findLeader(UUID teamId) {
        return teamMemberRepository.findLeaderByTeamId(teamId);
    }

    //Count helpers

    public long countMembersByRole(UUID teamId, TeamRole role) {
        return teamMemberRepository.countByTeamIdAndRole(teamId, role);
    }

    //Mappers 

    public TeamDto toDto(Team team) {
        TeamDto dto = new TeamDto();
        dto.setId(team.getId());
        dto.setName(team.getTeamName());
        dto.setDescription(team.getDescription());
        dto.setMemberCount((int) teamMemberRepository.countByTeamId(team.getId()));
        dto.setDocumentCount((int) documentRepository.countByTeamIdAndDeletedFalse(team.getId()));
        dto.setCreatedAt(team.getCreatedAt());
        dto.setUpdatedAt(team.getUpdatedAt());
        return dto;
    }

    public TeamMemberDto toMemberDto(TeamMember tm) {
        TeamMemberDto dto = new TeamMemberDto();
        dto.setId(tm.getId());
        dto.setUserId(tm.getUserId());
        dto.setFullName(tm.getFullName());
        dto.setTeamId(tm.getTeam().getId());
        dto.setTeamName(tm.getTeam().getTeamName());
        dto.setRole(tm.getRole() != null ? tm.getRole().name() : null);
        dto.setJoinedAt(tm.getJoinedAt());
        dto.setActive(tm.getActive());

        // Enrich with email and fullName from UserRepository
        userRepository.findById(tm.getUserId()).ifPresent(user -> {
            dto.setEmail(user.getEmail());
            if (dto.getFullName() == null || dto.getFullName().isBlank()) {
                dto.setFullName(user.getFullName());
            }
        });

        return dto;
    }

    public List<UserStatusDto> getTeamMemberStatuses(UUID teamId) {
        List<TeamMember> members = teamMemberRepository.findAllByTeamId(teamId);
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);

        return members.stream().map(member -> {
                boolean hasRecentActivity = userActivityRepository.existsByUserIdAndTeamIdAndOccurredAtAfter(
                    member.getUserId(), teamId, threshold);

            boolean isRecentlySeen = member.getLastSeen() != null && member.getLastSeen().isAfter(threshold);

            String status = (hasRecentActivity || isRecentlySeen) ? "ACTIVE" : "INACTIVE";

            return new UserStatusDto(member.getUserId(), teamId, status);
        }).collect(Collectors.toList());
    }
}
