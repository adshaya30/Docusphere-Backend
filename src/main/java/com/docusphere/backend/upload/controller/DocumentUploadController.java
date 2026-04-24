package com.docusphere.backend.upload.controller;

import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.Common.exception.FileUploadException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.response.ApiResponse;
import com.docusphere.backend.upload.constant.UploadConstant;
import com.docusphere.backend.upload.dto.ChunkUploadResponse;
import com.docusphere.backend.upload.dto.InitUploadRequest;
import com.docusphere.backend.upload.dto.InitUploadResponse;
import com.docusphere.backend.upload.service.DocumentUploadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@Validated
public class DocumentUploadController {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DocumentUploadController.class);

    private final DocumentUploadService uploadService;
    private final JwtService jwtService;

    public DocumentUploadController(DocumentUploadService uploadService, JwtService jwtService) {
        this.uploadService = uploadService;
        this.jwtService = jwtService;
    }

    // ---------------- INIT UPLOAD ----------------
    @PostMapping("/init-upload")
    public ResponseEntity<ApiResponse<InitUploadResponse>> initUpload(
            @Valid @RequestBody InitUploadRequest request
    ) {
        String fileId = uploadService.generateFileId();
        if (request.getFileSize() > UploadConstant.MAX_FILE_SIZE) {
            throw new FileUploadException("File exceeds 50MB limit");
        }

        LOGGER.info("Upload session created: fileId={}, fileName={}",
                fileId, request.getFileName());

        return ResponseEntity.ok(
                ApiResponse.success("Upload initialized", new InitUploadResponse(fileId))
        );
    }

    // ---------------- UPLOAD CHUNK ----------------
    @PostMapping(value = "/upload-chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ChunkUploadResponse>> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileName")@NotBlank String fileName,
            @RequestParam("fileId") @NotBlank String fileId,
            @RequestParam("chunkIndex") @Min(0) int chunkIndex,
            @RequestParam("totalChunks") @Min(1) int totalChunks,
            @RequestParam(value = "teamId", required = false) String teamId,
            @RequestHeader("Authorization") String token
    ) {

        if (token == null || !token.startsWith("Bearer ")) {
            throw new InvalidRequestException("Authorization header with Bearer token is required");
        }

        Long ownerId = jwtService.extractUserId(token.substring(7));
        UUID parsedTeamId = teamId != null && !teamId.isBlank() ? UUID.fromString(teamId.trim()) : null;

        LOGGER.debug("Chunk upload: fileId={}, chunkIndex={}/{}",
                fileId, chunkIndex, totalChunks);

        var result = uploadService.uploadChunk(
                file, fileName, fileId,
                chunkIndex, totalChunks,
                ownerId, parsedTeamId
        );

        return ResponseEntity.ok(
                ApiResponse.success(
                        result.isCompleted() ? "Upload completed" : "Chunk uploaded",
                        new ChunkUploadResponse(result.isCompleted(), result.getDocumentId())
                )
        );
    }
}