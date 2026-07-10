package com.docusphere.backend.documentVersion.service;

import com.docusphere.backend.authentication.entity.Role;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.documentShare.repository.DocumentShareRepository;
import com.docusphere.backend.documentShare.service.DocumentSharingService;
import com.docusphere.backend.onlyoffice.service.DocumentEditPermissionService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentVersionPermissionService Unit Tests")
class DocumentVersionPermissionServiceTest {

    @Mock
    private DocumentEditPermissionService editPermissionService;

    @Mock
    private DocumentShareRepository documentShareRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamService teamService;

    @Mock
    private DocumentSharingService documentSharingService;

    @InjectMocks
    private DocumentVersionPermissionService permissionService;

    private Long ownerId;
    private Long memberId;
    private UUID teamId;
    private Document document;

    @BeforeEach
    void setUp() {
        ownerId = 1L;
        memberId = 2L;
        teamId = UUID.randomUUID();

        document = Document.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .teamId(teamId)
                .name("Team Doc.docx")
                .build();
    }

    @Test
    @DisplayName("Should allow view when edit permission service allows view")
    void canView_delegatesToEditPermissionService() {
        when(editPermissionService.canView(document, memberId)).thenReturn(true);

        assertTrue(permissionService.canView(document, memberId));
    }

    @Test
    @DisplayName("Should deny view when edit permission service and share invite both fail")
    void canView_denied() {
        when(editPermissionService.canView(document, memberId)).thenReturn(false);

        assertFalse(permissionService.canView(document, memberId));
    }

    @Test
    @DisplayName("Should delegate restore permission to edit permission service")
    void canRestore_delegatesToEditPermissionService() {
        when(editPermissionService.canRestore(document, ownerId)).thenReturn(true);

        assertTrue(permissionService.canRestore(document, ownerId));
    }

    @Test
    @DisplayName("Should resolve owner editor role")
    void resolveEditorRole_owner() {
        assertEquals("OWNER", permissionService.resolveEditorRole(document, ownerId));
    }

    @Test
    @DisplayName("Should resolve team member editor role")
    void resolveEditorRole_teamMember() {
        TeamMember membership = new TeamMember();
        membership.setRole(TeamRole.MEMBER);

        when(teamService.findMembership(memberId, teamId)).thenReturn(Optional.of(membership));

        assertEquals("MEMBER", permissionService.resolveEditorRole(document, memberId));
    }

    @Test
    @DisplayName("Should resolve user role when editor is not owner or team member")
    void resolveEditorRole_userRoleFallback() {
        Document personalDocument = Document.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .teamId(null)
                .build();

        Role role = new Role();
        role.setName("ROLE_USER");

        User editor = new User();
        editor.setId(memberId);
        editor.setRole(role);

        when(userRepository.findById(memberId)).thenReturn(Optional.of(editor));

        assertEquals("USER", permissionService.resolveEditorRole(personalDocument, memberId));
    }
}
