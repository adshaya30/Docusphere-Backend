package com.docusphere.backend.upload.service;

import com.docusphere.backend.Common.exception.FileUploadException;
import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.docusphere.backend.upload.constant.UploadConstant.*;

@Service
public class DocumentUploadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentUploadService.class);

    private final DocumentRepository documentRepository;
    private final SupabaseStorageService supabaseStorageService;

    private final Path tempDir;
    private final String bucketName;

    public DocumentUploadService(
            DocumentRepository repository,
            SupabaseStorageService supabaseStorageService,
            @Value("${supabase.bucket.documents:documents}") String bucketName,
            @Value("${app.upload.dir:uploads}") String baseDir) throws Exception {

        this.documentRepository = repository;
        this.supabaseStorageService = supabaseStorageService;
        this.bucketName = bucketName;

        Path uploadDir = Paths.get(baseDir).toAbsolutePath().normalize();
        this.tempDir = uploadDir.resolve(TEMP_FOLDER);

        Files.createDirectories(tempDir);
    }

    // ---------------- FILE ID ----------------
    public String generateFileId() {
        return UUID.randomUUID().toString();
    }

    // ---------------- UPLOAD CHUNK ----------------
    public UploadResult uploadChunk(
            MultipartFile file,
            String fileName,
            String fileId,
            int chunkIndex,
            int totalChunks,
            Long ownerId,
            UUID teamId) {

        validateInput(file, fileName, fileId, chunkIndex, totalChunks, ownerId);
        validateTeamAccess(ownerId, teamId);

        Path sessionDir = tempDir.resolve(fileId);

        try {
            Files.createDirectories(sessionDir);

            saveChunk(file, sessionDir, chunkIndex);

            if (!isUploadComplete(sessionDir, totalChunks)) {
                return UploadResult.inProgress();
            }

            return finalizeUpload(fileName, fileId, sessionDir, totalChunks, ownerId, teamId);

        } catch (InvalidRequestException | FileUploadException ex) {
            throw ex;
        } catch (Exception e) {
            String rootCauseMessage = extractRootCauseMessage(e);
            LOGGER.error("Chunk upload failed: fileId={}, chunkIndex={}, totalChunks={}, reason={}",
                    fileId, chunkIndex, totalChunks, rootCauseMessage, e);
            throw new FileUploadException("Upload failed: " + rootCauseMessage, e);
        }
    }

    // ---------------- VALIDATION ----------------
    private void validateInput(
            MultipartFile file,
            String fileName,
            String fileId,
            int chunkIndex,
            int totalChunks,
            Long ownerId) {

        if (file == null || file.isEmpty())
            throw new FileUploadException("Empty file");

        if (fileName == null || fileName.isBlank())
            throw new InvalidRequestException("fileName required");

        if (fileId == null || fileId.isBlank())
            throw new InvalidRequestException("fileId required");

        if (ownerId == null)
            throw new InvalidRequestException("Invalid user");

        if (chunkIndex < MIN_CHUNK_INDEX || chunkIndex >= totalChunks)
            throw new InvalidRequestException("Invalid chunk index");

        if (totalChunks < MIN_TOTAL_CHUNKS)
            throw new InvalidRequestException("Invalid totalChunks");

        validateFileType(fileName);
    }

    private void validateFileType(String fileName) {

        if (!fileName.contains(".")) {
            throw new FileUploadException("Invalid file name");
        }

        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new FileUploadException("Unsupported file type: " + ext);
        }
    }

    // ---------------- SAVE CHUNK ----------------
    private void saveChunk(MultipartFile file, Path dir, int index) throws Exception {
        Path chunkPath = dir.resolve(CHUNK_PREFIX + index);

        if (!Files.exists(chunkPath)) {
            try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(
                    chunkPath,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
                file.getInputStream().transferTo(out);
            }
        }
    }

    private boolean isUploadComplete(Path dir, int totalChunks) {
        return IntStream.range(0, totalChunks)
                .allMatch(i -> Files.exists(dir.resolve(CHUNK_PREFIX + i)));
    }

    // ---------------- FINAL UPLOAD ----------------
    private UploadResult finalizeUpload(
            String fileName,
            String fileId,
            Path sessionDir,
            int totalChunks,
            Long ownerId,
            UUID teamId) throws Exception {

        try {
            if (documentRepository.findByFileId(fileId).isPresent()) {
                throw new InvalidRequestException("Duplicate upload");
            }

            String safeName = sanitize(fileName);
            String storageKey = fileId + "_" + safeName;

            java.io.File mergedFile = mergeChunks(sessionDir, totalChunks, fileId);

            if (mergedFile.length() > MAX_FILE_SIZE) {
                throw new FileUploadException("Max 50MB allowed");
            }

            if (mergedFile.length() <= 0) {
                throw new FileUploadException("Corrupted file");
            }

            String fileUrl = supabaseStorageService.uploadFile(
                    mergedFile,
                    bucketName,
                    storageKey);

            Document doc = Document.builder()
                    .fileId(fileId)
                    .name(safeName)
                    .type(getType(fileName))
                    .sizeBytes((long) mergedFile.length())
                    .ownerId(ownerId)
                    .teamId(teamId)
                    .storageKey(storageKey)
                    .fileUrl(fileUrl)
                    .status(Document.UploadStatus.COMPLETED)
                    .secured(false)
                    .build();

            Document saved = documentRepository.save(doc);
            return UploadResult.completed(saved.getId().toString());

        } finally {
            cleanup(sessionDir);
        }
    }

    // ---------------- MERGE ----------------
    private java.io.File mergeChunks(Path dir, int total, String fileId) throws Exception {
        Path mergedFile = dir.resolve(fileId + "_merged");

        try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(
                mergedFile,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE)) {
            for (int i = 0; i < total; i++) {
                java.nio.file.Files.copy(dir.resolve(CHUNK_PREFIX + i), out);
            }
        }

        return mergedFile.toFile();
    }

    // ---------------- CLEAN ----------------
    private void cleanup(Path dir) {
        try (var paths = Files.walk(dir)) {
            paths
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }

    // ---------------- HELPERS ----------------
    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String getType(String fileName) {
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private void validateTeamAccess(Long ownerId, UUID teamId) {
        if (teamId == null)
            return;

        // TEMP SAFE MODE (future team validation)
    }

    private String extractRootCauseMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }

        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return "unexpected server error";
        }

        return message;
    }

    // ---------------- RESULT ----------------
    public static class UploadResult {
        private final boolean completed;
        private final String documentId;

        public UploadResult(boolean completed, String documentId) {
            this.completed = completed;
            this.documentId = documentId;
        }

        public static UploadResult inProgress() {
            return new UploadResult(false, null);
        }

        public static UploadResult completed(String id) {
            return new UploadResult(true, id);
        }

        public boolean isCompleted() {
            return completed;
        }

        public String getDocumentId() {
            return documentId;
        }
    }
}