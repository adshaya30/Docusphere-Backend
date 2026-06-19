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

import org.springframework.context.annotation.Lazy;

@Service
@Lazy
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
        System.out.println("STAGE: UPLOAD_RECEIVED - Session ID: " + fileId + ", Chunk: " + chunkIndex + ", Size: " + file.getSize() + " bytes");
        log.info("STAGE: UPLOAD_RECEIVED - Session ID: {}, Chunk: {}, Size: {} bytes", fileId, chunkIndex, file.getSize());

        Path sessionDir = getSessionDir(fileId);
        if (!Files.exists(sessionDir)) {
            throw new InvalidRequestException("Upload session expired or invalid");
        }

        Path chunkPath = sessionDir.resolve(CHUNK_PREFIX + chunkIndex);
        file.transferTo(chunkPath.toAbsolutePath().toFile());
        log.info("Received OCR chunk {} for fileId {}", chunkIndex, fileId);
    }

    public File mergeChunks(String fileId, String fileName, int totalChunks) throws Exception {
        // Sanitize filename to prevent path traversal
        String safeFileName = Paths.get(fileName).getFileName().toString();
        
        System.out.println("STAGE: CHUNK_MERGE_STARTED - Session ID: " + fileId + ", File: " + safeFileName + ", Total Chunks: " + totalChunks);
        log.info("STAGE: CHUNK_MERGE_STARTED - Session ID: {}, File: {}, Total Chunks: {}", fileId, safeFileName, totalChunks);

        Path sessionDir = getSessionDir(fileId);
        Path mergedFilePath = sessionDir.resolve(fileId + "_merged_" + safeFileName);

        // Compute expected size by summing chunk file sizes
        long expectedSize = 0;
        for (int i = 0; i < totalChunks; i++) {
            Path chunkPath = sessionDir.resolve(CHUNK_PREFIX + i);
            if (!Files.exists(chunkPath)) {
                throw new Exception("Missing chunk " + i);
            }
            expectedSize += Files.size(chunkPath);
        }
        log.info("Total expected merged file size: {} bytes", expectedSize);

        try (OutputStream out = Files.newOutputStream(mergedFilePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            for (int i = 0; i < totalChunks; i++) {
                Path chunkPath = sessionDir.resolve(CHUNK_PREFIX + i);
                Files.copy(chunkPath, out);
            }
        }

        long actualSize = Files.size(mergedFilePath);
        log.info("Merged file actual size: {} bytes", actualSize);
        System.out.println("MERGE VERIFICATION - Expected Size: " + expectedSize + ", Actual Size: " + actualSize);

        if (actualSize != expectedSize) {
            throw new Exception("Merged file size mismatch! Expected " + expectedSize + " bytes, but got " + actualSize + " bytes.");
        }

        String hash = calculateSHA256(mergedFilePath.toFile());
        log.info("Merged file SHA-256 hash: {}", hash);
        System.out.println("MERGED FILE HASH: " + hash);

        // Copy merged file to debug-ocr directory
        try {
            Path debugDir = Paths.get("debug-ocr");
            if (!Files.exists(debugDir)) {
                Files.createDirectories(debugDir);
            }
            Path debugMergedPath = debugDir.resolve("merged_" + System.currentTimeMillis() + "_" + safeFileName);
            Files.copy(mergedFilePath, debugMergedPath);
            log.info("Saved copy of merged file to: {}", debugMergedPath.toAbsolutePath());
            System.out.println("SAVED MERGED COPY TO: " + debugMergedPath.toAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to copy merged file to debug-ocr: {}", e.getMessage());
        }

        System.out.println("STAGE: CHUNK_MERGE_COMPLETED - Session ID: " + fileId + ", File: " + safeFileName);
        log.info("STAGE: CHUNK_MERGE_COMPLETED - Session ID: {}, File: {}", fileId, safeFileName);

        return mergedFilePath.toFile();
    }

    public void cleanup(String fileId) {
        if (fileId == null || fileId.trim().isEmpty() || fileId.contains("/") || fileId.contains("\\") || fileId.contains("..")) {
            log.warn("Invalid fileId rejected for cleanup: {}", fileId);
            return;
        }
        log.info("Starting cleanup for session: {}", fileId);
        try {
            Path sessionDir = getSessionDir(fileId);
            if (Files.exists(sessionDir)) {
                try (var paths = Files.walk(sessionDir)) {
                    paths.sorted(java.util.Comparator.reverseOrder())
                         .map(Path::toFile)
                         .forEach(File::delete);
                }
                log.info("Successfully cleaned up session directory: {}", sessionDir);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup OCR upload session {}: {}", fileId, e.getMessage());
        }
    }

    private String calculateSHA256(java.io.File file) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream fis = new java.io.FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, n);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "HASH_ERROR: " + e.getMessage();
        }
    }

    private Path getSessionDir(String fileId) {
        return Paths.get(uploadDir, "ocr_temp", fileId).toAbsolutePath();
    }
}
