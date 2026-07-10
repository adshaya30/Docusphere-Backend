package com.docusphere.backend.ocr.service;

import com.docusphere.backend.ocr.dto.AiAnalysisOutcome;
import com.docusphere.backend.ocr.dto.OcrResponse;
import com.docusphere.backend.ocr.entity.OcrDocument;
import com.docusphere.backend.ocr.repository.OcrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Lazy
@Slf4j
public class OcrService {
    private final OcrRepository ocrRepository;
    private final GoogleVisionService googleVisionService;
    private final GeminiService geminiService;
    private final TextCleanupUtil textCleanupUtil;
    private final OcrJobManager ocrJobManager;
    private final OcrUploadService ocrUploadService;

    @Async
    public void processFileAsync(java.io.File file, String jobId, String uploadSessionId) {
        String filename = file.getName();
        try {
            // STEP 1: ADD FULL DEBUG LOGGING
            logPreVisionDebugInfo(file, uploadSessionId);

            // STEP 3: VALIDATE FINAL FILE BEFORE OCR
            validateFileHeader(file);

            if (!file.exists() || file.length() == 0) {
                throw new Exception("Merged file is empty or missing: " + file.getAbsolutePath());
            }
            String mimeType = Files.probeContentType(file.toPath());
            log.info("Processing merged file. Size: {} bytes, MIME: {}", file.length(), mimeType);

            // STEP 2: EXTRACTING (30%)
            ocrJobManager.updateJob(jobId, "EXTRACTING", 30);
            String rawText = googleVisionService.extractText(file.getAbsolutePath());
            
            // Clean extracted text
            String cleanedText = textCleanupUtil.cleanText(rawText);

            // STEP 3: SUMMARIZING (70%)
            ocrJobManager.updateJobWithPartial(jobId, "SUMMARIZING", 70, cleanedText);
            AiAnalysisOutcome result = geminiService.summarizeText(cleanedText);
            
            OcrResponse response = OcrResponse.builder()
                    .title(filename)
                    .description("AI-generated summary based on the extracted contents.")
                    .tags(result.getTags())
                    .keyPoints(result.getKeyPoints())
                    .summary(result.getSummary())
                    .extractedText(cleanedText)
                    .build();

            // DB save intentionally skipped — results are session-only (shown in frontend only)
            ocrJobManager.completeJob(jobId, response);
            
        } catch (Exception e) {
            log.error("Async OCR processing failed for {}: {}", filename, e.getMessage(), e);
            ocrJobManager.failJob(jobId, e.getMessage());
        } finally {
            // Cleanup the temporary local file and session - ACTUAL DELETION BYPASSED IN OcrUploadService
            if (uploadSessionId != null) {
                ocrUploadService.cleanup(uploadSessionId);
            }
        }
    }

    @Async
    public void processDocumentAsync(MultipartFile file, String jobId) {
        String filename = file.getOriginalFilename();
        Path tempFile = null;
        try {
            if (file.isEmpty() || file.getSize() == 0) {
                throw new Exception("Uploaded file is empty");
            }
            String mimeType = file.getContentType();
            log.info("Processing uploaded document. Size: {} bytes, MIME: {}", file.getSize(), mimeType);

            // STEP 2: EXTRACTING (30%)
            ocrJobManager.updateJob(jobId, "EXTRACTING", 30);
            
            tempFile = Files.createTempFile("ocr_async_", "_" + filename);
            file.transferTo(tempFile.toFile());

            // STEP 1: ADD FULL DEBUG LOGGING
            logPreVisionDebugInfo(tempFile.toFile(), null);

            // STEP 3: VALIDATE FINAL FILE BEFORE OCR
            validateFileHeader(tempFile.toFile());

            // STEP 2: EXTRACTING (50%)
            ocrJobManager.updateJob(jobId, "EXTRACTING", 50);
            String rawText = googleVisionService.extractText(tempFile.toAbsolutePath().toString());
            
            // Clean extracted text
            String cleanedText = textCleanupUtil.cleanText(rawText);

            // STEP 3: SUMMARIZING (80%)
            ocrJobManager.updateJobWithPartial(jobId, "SUMMARIZING", 80, cleanedText);
            AiAnalysisOutcome result = geminiService.summarizeText(cleanedText);

            OcrResponse response = OcrResponse.builder()
                    .title(filename)
                    .description("AI-generated summary based on the extracted contents.")
                    .tags(result.getTags())
                    .keyPoints(result.getKeyPoints())
                    .summary(result.getSummary())
                    .extractedText(cleanedText)
                    .build();

            // DB save intentionally skipped — results are session-only (shown in frontend only)
            ocrJobManager.completeJob(jobId, response);
            
        } catch (Exception e) {
            log.error("Async OCR processing failed for {}: {}", filename, e.getMessage(), e);
            ocrJobManager.failJob(jobId, e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    log.info("Deleted temporary upload file: {}", tempFile);
                } catch (Exception ex) {
                    log.warn("Failed to delete temp file {}: {}", tempFile, ex.getMessage());
                }
            }
        }
    }

    public OcrResponse processDocument(MultipartFile file) {
        String filename = file.getOriginalFilename();
        Path tempFile = null;
        try {
            if (file.isEmpty() || file.getSize() == 0) {
                throw new Exception("Uploaded file is empty");
            }
            String mimeType = file.getContentType();
            log.info("Processing uploaded document synchronously. Size: {} bytes, MIME: {}", file.getSize(), mimeType);

            // 1. Save to temp file
            tempFile = Files.createTempFile("ocr_", "_" + filename);
            file.transferTo(tempFile.toFile());
            
            // STEP 1: ADD FULL DEBUG LOGGING
            logPreVisionDebugInfo(tempFile.toFile(), null);

            // STEP 3: VALIDATE FINAL FILE BEFORE OCR
            validateFileHeader(tempFile.toFile());

            // 2. OCR Extraction
            String rawText = googleVisionService.extractText(tempFile.toAbsolutePath().toString());
            String cleanedText = textCleanupUtil.cleanText(rawText);
            
            // 3. AI Analysis
            AiAnalysisOutcome result = geminiService.summarizeText(cleanedText);
            
            log.info("--- Processing complete for: {} ---", filename);

            // 4. Build response
            OcrResponse response = OcrResponse.builder()
                    .title(filename)
                    .description("AI-generated summary based on the extracted contents.")
                    .tags(result.getTags())
                    .keyPoints(result.getKeyPoints())
                    .summary(result.getSummary())
                    .extractedText(cleanedText)
                    .build();

            // DB save intentionally skipped — results are session-only (shown in frontend only)
            return response;

        } catch (Exception e) {
            log.error("OCR processing failed for {}: {}", filename, e.getMessage(), e);
            throw new RuntimeException("Final processing failed: " + e.getMessage(), e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    log.info("Deleted temporary upload file: {}", tempFile);
                } catch (Exception ex) {
                    log.warn("Failed to delete temp file {}: {}", tempFile, ex.getMessage());
                }
            }
        }
    }

    private void validateFileHeader(java.io.File file) throws Exception {
        if (file == null) {
            throw new Exception("File is null");
        }
        if (!file.exists()) {
            throw new Exception("File does not exist: " + file.getAbsolutePath());
        }
        if (file.length() <= 0) {
            throw new Exception("File is empty: " + file.getAbsolutePath());
        }
        
        // Read first 4 bytes
        byte[] header = new byte[4];
        int bytesRead;
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            bytesRead = fis.read(header);
        }
        
        if (bytesRead < 3) {
            throw new Exception("File is too short to verify header: " + file.getAbsolutePath());
        }
        
        boolean isPng = (header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50 && (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47;
        boolean isJpg = (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
        boolean isPdf = (header[0] & 0xFF) == 0x25 && (header[1] & 0xFF) == 0x50 && (header[2] & 0xFF) == 0x44 && (header[3] & 0xFF) == 0x46;
        
        if (!isPng && !isJpg && !isPdf) {
            StringBuilder hexStr = new StringBuilder();
            for (int i = 0; i < Math.min(bytesRead, 4); i++) {
                hexStr.append(String.format("%02X ", header[i] & 0xFF));
            }
            throw new Exception("Invalid file magic bytes: [" + hexStr.toString().trim() + "]. Expected PNG (89 50 4E 47), JPG (FF D8 FF), or PDF (25 50 44 46).");
        }
        
        log.info("File header check passed. PNG: {}, JPG: {}, PDF: {}", isPng, isJpg, isPdf);
        System.out.println("HEADER VALIDATION PASSED - PNG: " + isPng + ", JPG: " + isJpg + ", PDF: " + isPdf);
    }

    private void logPreVisionDebugInfo(java.io.File file, String uploadSessionId) {
        try {
            String path = file.getAbsolutePath();
            boolean exists = file.exists();
            long size = exists ? file.length() : 0;
            String mimeType = exists ? Files.probeContentType(file.toPath()) : "N/A";
            
            String extension = "";
            int dotIndex = file.getName().lastIndexOf('.');
            if (dotIndex > 0) {
                extension = file.getName().substring(dotIndex);
            }
            
            boolean isPdf = extension.equalsIgnoreCase(".pdf");
            boolean isImage = extension.equalsIgnoreCase(".png") || extension.equalsIgnoreCase(".jpg") || extension.equalsIgnoreCase(".jpeg");
            boolean isTemp = path.contains("temp") || path.contains("tmp") || uploadSessionId != null;
            String sessionText = uploadSessionId != null ? uploadSessionId : "N/A";
            boolean isMerged = exists && file.getName().contains("merged");
            
            System.out.println("=== OCR DEEP DEBUG LOGGING ===");
            System.out.println("OCR FILE PATH: " + path);
            System.out.println("OCR FILE EXISTS: " + exists);
            System.out.println("OCR FILE SIZE: " + size + " bytes");
            System.out.println("OCR MIME TYPE: " + mimeType);
            System.out.println("OCR FILE EXTENSION: " + extension);
            System.out.println("OCR IS PDF: " + isPdf);
            System.out.println("OCR IS IMAGE: " + isImage);
            System.out.println("OCR IS TEMPORARY: " + isTemp);
            System.out.println("OCR UPLOAD SESSION ID: " + sessionText);
            System.out.println("OCR CHUNK MERGE COMPLETION STATUS: " + isMerged);
            System.out.println("==============================");
            
            log.info("=== OCR DEEP DEBUG LOGGING ===");
            log.info("OCR FILE PATH: {}", path);
            log.info("OCR FILE EXISTS: {}", exists);
            log.info("OCR FILE SIZE: {} bytes", size);
            log.info("OCR MIME TYPE: {}", mimeType);
            log.info("OCR FILE EXTENSION: {}", extension);
            log.info("OCR IS PDF: {}", isPdf);
            log.info("OCR IS IMAGE: {}", isImage);
            log.info("OCR IS TEMPORARY: {}", isTemp);
            log.info("OCR UPLOAD SESSION ID: {}", sessionText);
            log.info("OCR CHUNK MERGE COMPLETION STATUS: {}", isMerged);
            log.info("==============================");
        } catch (Exception e) {
            log.error("Failed to log debug info: {}", e.getMessage());
        }
    }

    @Async
    public void saveDocumentAsync(String filename, String extractedText, AiAnalysisOutcome analysis) {
        try {
            OcrDocument document = OcrDocument.builder()
                    .filename(filename)
                    .rawExtractedText(extractedText)
                    .aiSummary(analysis.getSummary())
                    .tags(analysis.getTags())
                    .keyPoints(analysis.getKeyPoints())
                    .build();
            ocrRepository.save(document);
            log.info("Document saved to DB: {}", filename);
        } catch (Exception e) {
            log.warn("Could not save document '{}' to DB (best-effort, user already received results): {}", filename, e.getMessage());
        }
    }
}
