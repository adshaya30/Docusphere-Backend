package com.docusphere.backend.onlyoffice.service;

import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.team.entity.Team;
import com.docusphere.backend.team.entity.TeamMember;
import com.docusphere.backend.team.entity.TeamRole;
import com.docusphere.backend.team.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentEditPermissionService Unit Tests")
class DocumentEditPermissionServiceTest {

    @Mock
    private TeamService teamService;

    @InjectMocks
    private DocumentEditPermissionService permissionService;

    private Long ownerId;
    private Long otherUserId;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        ownerId = 1L;
        otherUserId = 2L;
        teamId = UUID.randomUUID();
    }

    // ==================== PERSONAL DOCUMENT TESTS ====================

    @Test
    @DisplayName("Personal Document: Owner should have View, Download, and Edit access")
    void testPersonalDocument_OwnerAccess() {
        Document doc = Document.builder()
                .ownerId(ownerId)
                .teamId(null)
                .build();

        assertTrue(permissionService.canView(doc, ownerId));
        assertTrue(permissionService.canDownload(doc, ownerId));
        assertTrue(permissionService.canEdit(doc, ownerId));
    }

    @Test
    @DisplayName("Personal Document: Non-owner should NOT have View, Download, or Edit access")
    void testPersonalDocument_NonOwnerAccess() {
        Document doc = Document.builder()
                .ownerId(ownerId)
                .teamId(null)
                .build();

        assertFalse(permissionService.canView(doc, otherUserId));
        assertFalse(permissionService.canDownload(doc, otherUserId));
        assertFalse(permissionService.canEdit(doc, otherUserId));
    }

    // ==================== TEAM DOCUMENT TESTS ====================

    @Test
    @DisplayName("Team Document: Leader should have View, Download, and Edit access")
    void testTeamDocument_LeaderAccess() {
        Document doc = Document.builder()
                .ownerId(otherUserId) // leader is not the uploader
                .teamId(teamId)
                .build();

        TeamMember leaderMember = new TeamMember();
        leaderMember.setUserId(ownerId);
        leaderMember.setRole(TeamRole.LEADER);

        when(teamService.isUserInTeam(ownerId, teamId)).thenReturn(true);
        when(teamService.findMembership(ownerId, teamId)).thenReturn(Optional.of(leaderMember));

        assertTrue(permissionService.canView(doc, ownerId));
        assertTrue(permissionService.canDownload(doc, ownerId));
        assertTrue(permissionService.canEdit(doc, ownerId));
    }

    @Test
    @DisplayName("Team Document: Manager should have View, Download, and Edit access")
    void testTeamDocument_ManagerAccess() {
        Document doc = Document.builder()
                .ownerId(otherUserId) // manager is not the uploader
                .teamId(teamId)
                .build();

        TeamMember managerMember = new TeamMember();
        managerMember.setUserId(ownerId);
        managerMember.setRole(TeamRole.MANAGER);

        when(teamService.isUserInTeam(ownerId, teamId)).thenReturn(true);
        when(teamService.findMembership(ownerId, teamId)).thenReturn(Optional.of(managerMember));

        assertTrue(permissionService.canView(doc, ownerId));
        assertTrue(permissionService.canDownload(doc, ownerId));
        assertTrue(permissionService.canEdit(doc, ownerId));
    }

    @Test
    @DisplayName("Team Document: Member who is the Uploader should have View, Download, and Edit access")
    void testTeamDocument_MemberUploaderAccess() {
        Document doc = Document.builder()
                .ownerId(ownerId) // member IS the uploader
                .teamId(teamId)
                .build();

        TeamMember member = new TeamMember();
        member.setUserId(ownerId);
        member.setRole(TeamRole.MEMBER);

        when(teamService.isUserInTeam(ownerId, teamId)).thenReturn(true);
        when(teamService.findMembership(ownerId, teamId)).thenReturn(Optional.of(member));

        assertTrue(permissionService.canView(doc, ownerId));
        assertTrue(permissionService.canDownload(doc, ownerId));
        assertTrue(permissionService.canEdit(doc, ownerId));
    }

    @Test
    @DisplayName("Team Document: Member who is NOT the Uploader should have View and Download, but NOT Edit access")
    void testTeamDocument_NormalMemberAccess() {
        Document doc = Document.builder()
                .ownerId(otherUserId) // member is NOT the uploader
                .teamId(teamId)
                .build();

        TeamMember member = new TeamMember();
        member.setUserId(ownerId);
        member.setRole(TeamRole.MEMBER);

        when(teamService.isUserInTeam(ownerId, teamId)).thenReturn(true);
        when(teamService.findMembership(ownerId, teamId)).thenReturn(Optional.of(member));

        assertTrue(permissionService.canView(doc, ownerId));
        assertTrue(permissionService.canDownload(doc, ownerId));
        assertFalse(permissionService.canEdit(doc, ownerId));
    }

    @Test
    @DisplayName("Team Document: Non-team member should NOT have View, Download, or Edit access")
    void testTeamDocument_NonMemberAccess() {
        Document doc = Document.builder()
                .ownerId(ownerId)
                .teamId(teamId)
                .build();

        when(teamService.isUserInTeam(otherUserId, teamId)).thenReturn(false);
        when(teamService.findMembership(otherUserId, teamId)).thenReturn(Optional.empty());

        assertFalse(permissionService.canView(doc, otherUserId));
        assertFalse(permissionService.canDownload(doc, otherUserId));
        assertFalse(permissionService.canEdit(doc, otherUserId));
    }
}
