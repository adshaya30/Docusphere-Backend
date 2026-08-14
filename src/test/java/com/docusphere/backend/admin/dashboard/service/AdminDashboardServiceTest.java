package com.docusphere.backend.admin.dashboard.service;

import com.docusphere.backend.Common.exception.AdminDashboardException;
import com.docusphere.backend.admin.dashboard.dto.AdminDashboardDTO;
import com.docusphere.backend.admin.dashboard.repository.AdminDocumentRepository;
import com.docusphere.backend.admin.dashboard.repository.AdminTeamMemberRepository;
import com.docusphere.backend.admin.dashboard.repository.AdminTeamRepository;
import com.docusphere.backend.admin.dashboard.repository.AdminUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminDashboardService Unit Tests")
class AdminDashboardServiceTest {

    @Mock
    private AdminUserRepository userRepository;

    @Mock
    private AdminDocumentRepository documentRepository;

    @Mock
    private AdminTeamRepository teamRepository;

    @Mock
    private AdminTeamMemberRepository teamMemberRepository;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adminDashboardService, "storageQuotaBytes", 1073741824L);
    }

    @Test
    @DisplayName("Should compile and return dashboard data successfully")
    void testGetDashboardData_Success() {
        // Arrange
        when(userRepository.count()).thenReturn(10L);
        when(documentRepository.count()).thenReturn(50L);
        when(teamRepository.count()).thenReturn(5L);
        when(userRepository.countActiveSessions()).thenReturn(3L);

        when(documentRepository.getCurrentMonthDocumentCount()).thenReturn(20L);
        when(documentRepository.getPreviousMonthDocumentCount()).thenReturn(10L);

        when(userRepository.getCurrentMonthUserCount()).thenReturn(4L);
        when(userRepository.getPreviousMonthUserCount()).thenReturn(2L);

        when(teamRepository.getCurrentMonthTeamCount()).thenReturn(2L);
        when(teamRepository.getPreviousMonthTeamCount()).thenReturn(1L);

        List<Object[]> mockMonthly = new ArrayList<>();
        mockMonthly.add(new Object[]{7, 15L});
        when(documentRepository.getMonthlyUploads()).thenReturn(mockMonthly);

        List<Object[]> mockTopTeams = new ArrayList<>();
        mockTopTeams.add(new Object[]{"Team A", 12L, 80.0});
        when(teamMemberRepository.getTopTeamsByMembers()).thenReturn(mockTopTeams);

        when(documentRepository.sumTotalStorageBytes()).thenReturn(102400L);

        // Act
        AdminDashboardDTO result = adminDashboardService.getDashboardData();

        // Assert
        assertNotNull(result);
        assertEquals(10L, result.getTotalUsers());
        assertEquals(50L, result.getTotalDocuments());
        assertEquals(5L, result.getTotalTeams());
        assertEquals(3L, result.getActiveSessions());

        // Growth: ((20 - 10) / 10) * 100 = 100.0%
        assertEquals(100.0, result.getDocumentGrowth());
        // User growth: ((4 - 2) / 2) * 100 = 100.0%
        assertEquals(100.0, result.getUserGrowth());
        // Team growth: ((2 - 1) / 1) * 100 = 100.0%
        assertEquals(100.0, result.getTeamGrowth());

        assertEquals(1, result.getMonthlyUploads().size());
        assertEquals(7, result.getMonthlyUploads().get(0).getMonth());
        assertEquals(15L, result.getMonthlyUploads().get(0).getMonthlyUploads());

        assertEquals(1, result.getTopTeams().size());
        assertEquals("Team A", result.getTopTeams().get(0).getTeamName());
        assertEquals(12L, result.getTopTeams().get(0).getDocumentCount());
        assertEquals(80.0, result.getTopTeams().get(0).getActivityPercentage());

        assertEquals(102400L, result.getUsedStorageBytes());
        assertEquals(1073741824L, result.getStorageQuotaBytes());
    }

    @Test
    @DisplayName("Should fallback to active team members count when active session query returns null or zero")
    void testGetDashboardData_SessionCountFallback() {
        // Arrange
        when(userRepository.count()).thenReturn(10L);
        when(documentRepository.count()).thenReturn(50L);
        when(teamRepository.count()).thenReturn(5L);
        when(userRepository.countActiveSessions()).thenReturn(0L); // 0 sessions
        when(teamMemberRepository.countDistinctActiveUsers()).thenReturn(4L); // Fallback active users

        when(documentRepository.getCurrentMonthDocumentCount()).thenReturn(20L);
        when(documentRepository.getPreviousMonthDocumentCount()).thenReturn(10L);
        when(userRepository.getCurrentMonthUserCount()).thenReturn(4L);
        when(userRepository.getPreviousMonthUserCount()).thenReturn(2L);
        when(teamRepository.getCurrentMonthTeamCount()).thenReturn(2L);
        when(teamRepository.getPreviousMonthTeamCount()).thenReturn(1L);

        when(documentRepository.getMonthlyUploads()).thenReturn(null);
        when(teamMemberRepository.getTopTeamsByMembers()).thenReturn(null);
        when(documentRepository.sumTotalStorageBytes()).thenReturn(null);

        // Act
        AdminDashboardDTO result = adminDashboardService.getDashboardData();

        // Assert
        assertNotNull(result);
        assertEquals(4L, result.getActiveSessions()); // Should use fallback
        assertEquals(0L, result.getUsedStorageBytes());
    }

    @Test
    @DisplayName("Should throw AdminDashboardException when critical repository throws exception")
    void testGetDashboardData_ExceptionHandled() {
        // Arrange
        when(userRepository.count()).thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        assertThrows(AdminDashboardException.class, () -> adminDashboardService.getDashboardData());
    }
}
