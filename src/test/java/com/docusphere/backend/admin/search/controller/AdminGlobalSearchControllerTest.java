package com.docusphere.backend.admin.search.controller;

import com.docusphere.backend.admin.search.dto.GlobalSearchResultDTO;
import com.docusphere.backend.admin.search.service.AdminGlobalSearchService;
import com.docusphere.backend.authentication.service.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminGlobalSearchController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminGlobalSearchController Unit Tests")
class AdminGlobalSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminGlobalSearchService searchService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("Should return global search results successfully")
    void testGlobalSearch_Success() throws Exception {
        // Arrange
        GlobalSearchResultDTO.UserSearchResult userRes = new GlobalSearchResultDTO.UserSearchResult(
                1L, "Admin User", "admin@docusphere.com", "ADMIN", "user"
        );
        GlobalSearchResultDTO mockResult = new GlobalSearchResultDTO(
                Collections.singletonList(userRes),
                Collections.emptyList(),
                Collections.emptyList(),
                1L
        );

        when(searchService.globalSearch("Admin")).thenReturn(mockResult);

        // Act & Assert
        mockMvc.perform(get("/api/admin/search/global").param("query", "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[0].fullName").value("Admin User"))
                .andExpect(jsonPath("$.totalResults").value(1));
    }
}
