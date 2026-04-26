package com.docusphere.backend.ocr.service;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.UUID;

@Service
@Slf4j
public class OcrUploadService {

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private static final String CHUNK_PREFIX = "chunk_";

    public String initUpload(String fileName) throws Exception {
        String fileId = UUID.randomUUID().toString();
        Path sessionDir = getSessionDir(fileId);
        Files.createDirectories(sessionDir);
        log.info("Initialized OCR upload session: {} for file: {}", fileId, fileName);
        return fileId;
    }

    public void uploadChunk(String fileId, int chunkIndex, MultipartFile file) throws Exception {
        Path sessionDir = getSessionDir(fileId);
        if (!Files.exists(sessionDir)) {
            throw new InvalidRequestException("Upload session expired or invalid");
        }

        Path chunkPath = sessionDir.resolve(CHUNK_PREFIX + chunkIndex);
        file.transferTo(chunkPath.toAbsolutePath().toFile());
        log.info("Received OCR chunk {} for fileId {}", chunkIndex, fileId);
    }

    public File mergeChunks(String fileId, String fileName, int totalChunks) throws Exception {
        Path sessionDir = getSessionDir(fileId);
        Path mergedFilePath = sessionDir.resolve(fileId + "_merged_" + fileName);

        try (OutputStream out = Files.newOutputStream(mergedFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            for (int i = 0; i < totalChunks; i++) {
                Path chunkPath = sessionDir.resolve(CHUNK_PREFIX + i);
                if (!Files.exists(chunkPath)) {
                    throw new Exception("Missing chunk " + i);
                }
                Files.copy(chunkPath, out);
            }
        }

        log.info("Successfully merged OCR file: {} (ID: {})", fileName, fileId);
        return mergedFilePath.toFile();
    }

    public void cleanup(String fileId) {
        try {
            Path sessionDir = getSessionDir(fileId);
            if (Files.exists(sessionDir)) {
                try (var paths = Files.walk(sessionDir)) {
                    paths.sorted(Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(File::delete);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup OCR upload session {}: {}", fileId, e.getMessage());
        }
    }

    private Path getSessionDir(String fileId) {
        return Paths.get(uploadDir, "ocr_temp", fileId).toAbsolutePath();
    }
}
