package com.docusphere.backend.myDocuments.controller;

import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.myDocuments.dto.MyDocumentItemResponse;
import com.docusphere.backend.myDocuments.dto.MyDocumentsPageResponse;
import com.docusphere.backend.myDocuments.service.MyDocumentsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MyDocumentController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("MyDocumentController Unit Tests")
class MyDocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MyDocumentsService myDocumentsService;

    @MockitoBean
    private JwtService jwtService;

    private String authToken;
    private Long userId;
    private UUID teamId;

    @BeforeEach
    void setUp() {
        userId = 123L;
        teamId = UUID.randomUUID();
        authToken = "Bearer test-jwt-token";
    }

    // ==================== GET DOCUMENTS TESTS ====================

    @Test
    @DisplayName("Should successfully fetch user documents")
    void testGetDocuments_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        MyDocumentItemResponse item = MyDocumentItemResponse.builder()
                .id(UUID.randomUUID().toString())
                .fileId("file123")
                .name("Test Document")
                .type("pdf")
                .sizeBytes(1024L)
                .ownerId(userId.toString())
                .teamId(null)
                .starred(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(MyDocumentItemResponse.UploadStatus.COMPLETED)
                .build();

        MyDocumentsPageResponse pageResponse = MyDocumentsPageResponse.builder()
                .items(Collections.singletonList(item))
                .page(0)
                .size(15)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();

        when(myDocumentsService.getDocuments(
                eq(userId), isNull(), eq(0), eq(15), eq("createdAt"), eq("DESC"),
                isNull(), isNull(), isNull()
        )).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/my-documents")
                .header("Authorization", authToken)
                .param("page", "0")
                .param("size", "15")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("Test Document"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("Should fetch documents with team filter")
    void testGetDocuments_WithTeam_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        MyDocumentItemResponse item = MyDocumentItemResponse.builder()
                .id(UUID.randomUUID().toString())
                .fileId("file123")
                .name("Team Document")
                .type("pdf")
                .sizeBytes(1024L)
                .ownerId(userId.toString())
                .teamId(teamId.toString())
                .starred(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(MyDocumentItemResponse.UploadStatus.COMPLETED)
                .build();

        MyDocumentsPageResponse pageResponse = MyDocumentsPageResponse.builder()
                .items(Collections.singletonList(item))
                .page(0)
                .size(15)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();

        when(myDocumentsService.getDocuments(
                eq(userId), eq(teamId), eq(0), eq(15), eq("createdAt"), eq("DESC"),
                isNull(), isNull(), isNull()
        )).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/my-documents")
                .header("Authorization", authToken)
                .param("teamId", teamId.toString())
                .param("page", "0")
                .param("size", "15")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("Team Document"))
                .andExpect(jsonPath("$.data.items[0].teamId").value(teamId.toString()));
    }

    @Test
    @DisplayName("Should filter documents by type")
    void testGetDocuments_FilterByType_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        MyDocumentItemResponse item = MyDocumentItemResponse.builder()
                .id(UUID.randomUUID().toString())
                .fileId("file456")
                .name("Spreadsheet.xlsx")
                .type("xlsx")
                .sizeBytes(2048L)
                .ownerId(userId.toString())
                .teamId(null)
                .starred(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(MyDocumentItemResponse.UploadStatus.COMPLETED)
                .build();

        MyDocumentsPageResponse pageResponse = MyDocumentsPageResponse.builder()
                .items(Collections.singletonList(item))
                .page(0)
                .size(15)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();

        when(myDocumentsService.getDocuments(
                eq(userId), isNull(), eq(0), eq(15), eq("createdAt"), eq("DESC"),
                eq("xlsx"), isNull(), isNull()
        )).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/my-documents")
                .header("Authorization", authToken)
                .param("page", "0")
                .param("size", "15")
                .param("sortBy", "createdAt")
                .param("sortDirection", "DESC")
                .param("type", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].type").value("xlsx"));
    }

    @Test
    @DisplayName("Should filter starred documents")
    void testGetDocuments_FilterStarred_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        MyDocumentItemResponse item = MyDocumentItemResponse.builder()
                .id(UUID.randomUUID().toString())
                .fileId("file789")
                .name("Important Document")
                .type("pdf")
                .sizeBytes(1024L)
                .ownerId(userId.toString())
                .teamId(null)
                .starred(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(MyDocumentItemResponse.UploadStatus.COMPLETED)
                .build();

        MyDocumentsPageResponse pageResponse = MyDocumentsPageResponse.builder()
                .items(Collections.singletonList(item))
                .page(0)
                .size(15)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();

        when(myDocumentsService.getDocuments(
                eq(userId), isNull(), eq(0), eq(15), eq("createdAt"), eq("DESC"),
                isNull(), eq(true), isNull()
        )).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/my-documents")
                .header("Authorization", authToken)
                .param("page", "0")
                .param("size", "15")
                .param("starred", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].starred").value(true));
    }

    @Test
    @DisplayName("Should search documents by name")
    void testGetDocuments_Search_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        MyDocumentItemResponse item = MyDocumentItemResponse.builder()
                .id(UUID.randomUUID().toString())
                .fileId("file101")
                .name("React Tutorial")
                .type("pdf")
                .sizeBytes(3072L)
                .ownerId(userId.toString())
                .teamId(null)
                .starred(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .status(MyDocumentItemResponse.UploadStatus.COMPLETED)
                .build();

        MyDocumentsPageResponse pageResponse = MyDocumentsPageResponse.builder()
                .items(Collections.singletonList(item))
                .page(0)
                .size(15)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();

        when(myDocumentsService.getDocuments(
                eq(userId), isNull(), eq(0), eq(15), eq("createdAt"), eq("DESC"),
                isNull(), isNull(), eq("React")
        )).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/my-documents")
                .header("Authorization", authToken)
                .param("page", "0")
                .param("size", "15")
                .param("search", "React"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("React Tutorial"));
    }

    @Test
    @DisplayName("Should handle custom pagination")
    void testGetDocuments_CustomPagination_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        MyDocumentsPageResponse pageResponse = MyDocumentsPageResponse.builder()
                .items(Collections.emptyList())
                .page(2)
                .size(25)
                .totalElements(50)
                .totalPages(2)
                .first(false)
                .last(true)
                .empty(false)
                .build();

        when(myDocumentsService.getDocuments(
                eq(userId), isNull(), eq(2), eq(25), eq("createdAt"), eq("DESC"),
                isNull(), isNull(), isNull()
        )).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/my-documents")
                .header("Authorization", authToken)
                .param("page", "2")
                .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(25))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    @DisplayName("Should sort by different fields")
    void testGetDocuments_SortByName_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        MyDocumentsPageResponse pageResponse = MyDocumentsPageResponse.builder()
                .items(Collections.emptyList())
                .page(0)
                .size(15)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .build();

        when(myDocumentsService.getDocuments(
                eq(userId), isNull(), eq(0), eq(15), eq("name"), eq("ASC"),
                isNull(), isNull(), isNull()
        )).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/my-documents")
                .header("Authorization", authToken)
                .param("sortBy", "name")
                .param("sortDirection", "ASC"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should return empty list when no documents found")
    void testGetDocuments_Empty_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        MyDocumentsPageResponse pageResponse = MyDocumentsPageResponse.builder()
                .items(Collections.emptyList())
                .page(0)
                .size(15)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .build();

        when(myDocumentsService.getDocuments(
                eq(userId), isNull(), anyInt(), anyInt(), anyString(), anyString(),
                isNull(), isNull(), isNull()
        )).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/my-documents")
                .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.empty").value(true))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    @Test
    @DisplayName("Should return multiple documents")
    void testGetDocuments_MultipleDocuments_Success() throws Exception {
        // Arrange
        when(jwtService.extractUserId("test-jwt-token")).thenReturn(userId);

        MyDocumentItemResponse item1 = MyDocumentItemResponse.builder()
                .id(UUID.randomUUID().toString())
                .name("Document 1")
                .type("pdf")
                .sizeBytes(1024L)
                .ownerId(userId.toString())
                .build();

        MyDocumentItemResponse item2 = MyDocumentItemResponse.builder()
                .id(UUID.randomUUID().toString())
                .name("Document 2")
                .type("docx")
                .sizeBytes(2048L)
                .ownerId(userId.toString())
                .build();

        MyDocumentsPageResponse pageResponse = MyDocumentsPageResponse.builder()
                .items(Arrays.asList(item1, item2))
                .page(0)
                .size(15)
                .totalElements(2)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();

        when(myDocumentsService.getDocuments(
                eq(userId), isNull(), anyInt(), anyInt(), anyString(), anyString(),
                isNull(), isNull(), isNull()
        )).thenReturn(pageResponse);

        // Act & Assert
        mockMvc.perform(get("/api/my-documents")
                .header("Authorization", authToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].name").value("Document 1"))
                .andExpect(jsonPath("$.data.items[1].name").value("Document 2"));
    }

    @Test
    @DisplayName("Should throw exception when authorization header missing")
    void testGetDocuments_NoAuthHeader_ThrowsException() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/my-documents")
                .param("page", "0")
                .param("size", "15"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should throw exception when authorization header invalid format")
    void testGetDocuments_InvalidAuthFormat_ThrowsException() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/my-documents")
                .header("Authorization", "InvalidFormat test-token")
                .param("page", "0")
                .param("size", "15"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should validate page parameter")
    void testGetDocuments_InvalidPage_BadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/my-documents")
                .header("Authorization", authToken)
                .param("page", "-1")
                .param("size", "15"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should validate size parameter")
    void testGetDocuments_InvalidSize_BadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/my-documents")
                .header("Authorization", authToken)
                .param("page", "0")
                .param("size", "0"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Should validate max size parameter")
    void testGetDocuments_SizeExceedsMax_BadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/my-documents")
                .header("Authorization", authToken)
                .param("page", "0")
                .param("size", "150"))
                .andExpect(status().isInternalServerError());
    }
}

