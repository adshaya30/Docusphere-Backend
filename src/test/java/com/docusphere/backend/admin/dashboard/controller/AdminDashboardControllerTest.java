package com.docusphere.backend.admin.dashboard.controller;

import com.docusphere.backend.admin.dashboard.dto.AdminDashboardDTO;
import com.docusphere.backend.admin.dashboard.service.AdminDashboardService;
import com.docusphere.backend.authentication.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.core.userdetails.UserDetailsService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminDashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminDashboardController Unit Tests")
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminDashboardService adminDashboardService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("Should return admin dashboard data successfully")
    void testGetDashboard_Success() throws Exception {
        // Arrange
        AdminDashboardDTO mockDto = new AdminDashboardDTO();
        mockDto.setTotalUsers(10L);
        mockDto.setTotalDocuments(50L);
        mockDto.setTotalTeams(5L);
        mockDto.setActiveSessions(3L);

        when(adminDashboardService.getDashboardData()).thenReturn(mockDto);

        // Act & Assert
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(10))
                .andExpect(jsonPath("$.totalDocuments").value(50))
                .andExpect(jsonPath("$.totalTeams").value(5))
                .andExpect(jsonPath("$.activeSessions").value(3));
    }
}
