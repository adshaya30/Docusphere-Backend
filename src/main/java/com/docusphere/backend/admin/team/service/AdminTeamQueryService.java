package com.docusphere.backend.admin.team.service;

import com.docusphere.backend.admin.team.dto.AdminMemberView;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.entity.TeamInvitation;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.repository.TeamInvitationRepository;
import com.docusphere.backend.team.repository.TeamMemberRepository;
import com.docusphere.backend.team.repository.TeamRepository;
import com.docusphere.backend.document.repository.DocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminTeamQueryService {
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInvitationRepository teamInvitationRepository;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;

    public AdminTeamQueryService(TeamRepository tr, TeamMemberRepository tmr, TeamInvitationRepository tir, 
                                 UserRepository ur, DocumentRepository dr) {
        this.teamRepository = tr; this.teamMemberRepository = tmr; this.teamInvitationRepository = tir; 
        this.userRepository = ur; this.documentRepository = dr;
    }

    public Team assertTeamExists(UUID tid) {
        return teamRepository.findById(tid).orElseThrow(() -> new EntityNotFoundException("Team not found: " + tid));
    }

    public List<AdminMemberView> getTeamMembers(UUID tid) {
        assertTeamExists(tid);
        List<AdminMemberView> list = teamMemberRepository.findAllByTeamId(tid).stream().map(this::toAdminMemberView).collect(Collectors.toList());
        list.addAll(teamInvitationRepository.findAllByTeamId(tid).stream().map(this::toAdminMemberViewFromInvitation).toList());
        return list;
    }

    public AdminMemberView toAdminMemberView(TeamMember tm) {
        AdminMemberView v = new AdminMemberView();
        v.setMembershipId(tm.getId()); v.setUserId(tm.getUserId()); v.setFullName(tm.getFullName());
        v.setRole(tm.getRole() != null ? tm.getRole().name() : null);
        v.setTeamId(tm.getTeam().getId()); v.setTeamName(tm.getTeam().getTeamName()); v.setJoinedAt(tm.getJoinedAt());
        
        // Activity Logic: Active if seen within 7 days OR document activity within 7 days
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        boolean isRecentSeen = tm.getLastSeen() != null && tm.getLastSeen().isAfter(cutoff);
        boolean hasRecentDocActivity = documentRepository.existsByOwnerIdAndUpdatedAtAfter(tm.getUserId(), cutoff);
        
        v.setStatus((isRecentSeen || hasRecentDocActivity) ? "Active" : "Joined");
        
        userRepository.findById(tm.getUserId()).ifPresent(u -> { v.setEmail(u.getEmail()); if (v.getFullName() == null || v.getFullName().isBlank()) v.setFullName(u.getFullName()); });
        return v;
    }

    public AdminMemberView toAdminMemberViewFromInvitation(TeamInvitation inv) {
        AdminMemberView v = new AdminMemberView();
        v.setEmail(inv.getEmail()); v.setRole(inv.getRole().name()); v.setTeamId(inv.getTeamId()); v.setFullName("Pending Invitation");
        v.setStatus("Pending");
        v.setMembershipId(inv.getId()); // Use the actual DB UUID so delete/role-change works
        return v;
    }
}
