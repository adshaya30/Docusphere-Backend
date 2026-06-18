package com.docusphere.backend.documentProtection.controller;

import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.documentProtection.dto.DocumentProtectionResponse;
import com.docusphere.backend.documentProtection.dto.PasswordVerificationResponse;
import com.docusphere.backend.documentProtection.service.DocumentPasswordProtectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.Cookie;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DocumentProtectionController.class)
@AutoConfigureMockMvc(addFilters = false)
class DocumentProtectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentPasswordProtectionService protectionService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void enableProtection_shouldReturnSuccess() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(jwtService.extractUserId(anyString())).thenReturn(10L);
        when(protectionService.enableProtection(eq(10L), eq(documentId), eq("secure123")))
                .thenReturn(DocumentProtectionResponse.builder()
                        .documentId(documentId)
                        .name("report.pdf")
                        .passwordProtected(true)
                        .updatedAt(LocalDateTime.now())
                        .build());

        mockMvc.perform(post("/api/documents/{id}/protect", documentId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secure123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordProtected").value(true));
    }

    @Test
    void verifyPassword_shouldReturnVerified() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(jwtService.extractUserId(anyString())).thenReturn(10L);
        when(protectionService.verifyPassword(eq(10L), eq(documentId), eq("secure123"), isNull()))
                .thenReturn(PasswordVerificationResponse.builder()
                        .documentId(documentId)
                        .verified(true)
                        .build());

        mockMvc.perform(post("/api/documents/{id}/verify-password", documentId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secure123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true));
    }

    @Test
    void verifyPassword_withAccessTokenCookie_shouldReturnVerified() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(jwtService.extractUserId("cookie-token")).thenReturn(10L);
        when(protectionService.verifyPassword(eq(10L), eq(documentId), eq("secure123"), isNull()))
                .thenReturn(PasswordVerificationResponse.builder()
                        .documentId(documentId)
                        .verified(true)
                        .build());

        mockMvc.perform(post("/api/documents/{id}/verify-password", documentId)
                        .cookie(new Cookie("accessToken", "cookie-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secure123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true));
    }
}
