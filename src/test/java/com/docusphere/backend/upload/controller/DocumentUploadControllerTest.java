package com.docusphere.backend.upload.controller;

import com.docusphere.backend.Common.exception.GlobalExceptionHandler;
import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.upload.service.DocumentUploadService;
import com.docusphere.backend.authentication.service.security.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DocumentUploadController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DocumentUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentUploadService uploadService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void initUpload_shouldReturnFileIdForValidPayload() throws Exception {
        when(uploadService.generateFileId()).thenReturn("file-123");

        String requestJson = """
                {
                  "fileName": "report.pdf",
                  "fileSize": 1024
                }
                """;

        mockMvc.perform(post("/api/documents/init-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Upload initialized"))
                .andExpect(jsonPath("$.data.fileId").value("file-123"));
    }

    @Test
    void initUpload_shouldReturnValidationErrorWhenFileNameMissing() throws Exception {
        String requestJson = """
                {
                  "fileName": "",
                  "fileSize": 1024
                }
                """;

        mockMvc.perform(post("/api/documents/init-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("fileName: fileName is required"));
    }

    @Test
    void initUpload_shouldReturnValidationErrorWhenFileSizeInvalid() throws Exception {
        String requestJson = """
                {
                  "fileName": "report.pdf",
                  "fileSize": 0
                }
                """;

        mockMvc.perform(post("/api/documents/init-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("fileSize: fileSize must be greater than 0"));
    }

    @Test
    void initUpload_shouldReturnBadRequestWhenFileExceedsLimit() throws Exception {
        when(uploadService.generateFileId()).thenReturn("file-over-limit");

        String requestJson = """
                {
                  "fileName": "big.pdf",
                  "fileSize": 60000000
                }
                """;

        mockMvc.perform(post("/api/documents/init-upload")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FILE_UPLOAD_ERROR"))
                .andExpect(jsonPath("$.message").value("File exceeds 50MB limit"));
    }

    @Test
    void uploadChunk_shouldReturnInProgressResponseWhenUploadNotFinished() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "report.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "hello".getBytes()
        );

        when(jwtService.extractUserId("mocked-jwt-token")).thenReturn(45L);
        when(uploadService.uploadChunk(any(), eq("report.pdf"), eq("file-abc"), eq(0), eq(2), eq(45L), isNull()))
                .thenReturn(DocumentUploadService.UploadResult.inProgress());

        mockMvc.perform(multipart("/api/documents/upload-chunk")
                        .file(multipartFile)
                        .param("fileName", "report.pdf")
                        .param("fileId", "file-abc")
                        .param("chunkIndex", "0")
                        .param("totalChunks", "2")
                        .header("Authorization", "Bearer mocked-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Chunk uploaded"))
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.documentId").doesNotExist());

        verify(jwtService).extractUserId("mocked-jwt-token");
    }

    @Test
    void uploadChunk_shouldReturnCompletedResponseWhenServiceCompletesUpload() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "report.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "hello".getBytes()
        );
        String teamId = UUID.randomUUID().toString();
        String bearerToken = "Bearer mocked-jwt-token";

        when(jwtService.extractUserId("mocked-jwt-token")).thenReturn(45L);
        when(uploadService.uploadChunk(any(), eq("report.pdf"), eq("file-abc"), eq(0), eq(1), eq(45L), eq(UUID.fromString(teamId))))
                .thenReturn(DocumentUploadService.UploadResult.completed("doc-88"));

        mockMvc.perform(multipart("/api/documents/upload-chunk")
                        .file(multipartFile)
                        .param("fileName", "report.pdf")
                        .param("fileId", "file-abc")
                        .param("chunkIndex", "0")
                        .param("totalChunks", "1")
                        .param("teamId", teamId)
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Upload completed"))
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.documentId").value("doc-88"));

        verify(jwtService).extractUserId("mocked-jwt-token");
    }

    @Test
    void uploadChunk_shouldReturnBadRequestWhenTeamIdIsMalformed() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "report.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "hello".getBytes()
        );

        when(jwtService.extractUserId("mocked-jwt-token")).thenReturn(45L);

        mockMvc.perform(multipart("/api/documents/upload-chunk")
                        .file(multipartFile)
                        .param("fileName", "report.pdf")
                        .param("fileId", "file-abc")
                        .param("chunkIndex", "0")
                        .param("totalChunks", "1")
                        .param("teamId", "not-a-uuid")
                        .header("Authorization", "Bearer mocked-jwt-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("teamId must be a valid UUID"));
    }

    @Test
    void uploadChunk_shouldReturnBadRequestWhenBearerHeaderMissing() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "report.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "hello".getBytes()
        );

        mockMvc.perform(multipart("/api/documents/upload-chunk")
                        .file(multipartFile)
                        .param("fileName", "report.pdf")
                        .param("fileId", "file-abc")
                        .param("chunkIndex", "0")
                        .param("totalChunks", "1")
                        .header("Authorization", "token-without-bearer-prefix"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Authorization header or accessToken cookie is required"));
    }
}
