package com.docusphere.backend.ocr.service;

import com.docusphere.backend.ocr.dto.AiAnalysisOutcome;
import com.docusphere.backend.ocr.dto.OcrResponse;
import com.docusphere.backend.ocr.entity.OcrDocument;
import com.docusphere.backend.ocr.repository.OcrRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {
    private final OcrRepository ocrRepository;
    private final PythonServiceCaller pythonServiceCaller;
    private final OcrJobManager ocrJobManager;

    @Async
    public void processDocumentAsync(MultipartFile file, String jobId) {
        String filename = file.getOriginalFilename();
        try {
            // 1. Uploading step (already started)
            ocrJobManager.updateJob(jobId, "EXTRACTING", 30);
            
            Path tempFile = Files.createTempFile("ocr_async_", "_" + filename);
            file.transferTo(tempFile.toFile());

            // 2. Extracting & Summarizing (Combined in persistent service)
            // We can split the updates to make it feel more real
            ocrJobManager.updateJob(jobId, "EXTRACTING", 50);
            
            PythonServiceCaller.ProcessResult result = pythonServiceCaller.processDocument(tempFile.toAbsolutePath().toString());
            
            ocrJobManager.updateJob(jobId, "SUMMARIZING", 80);
            
            Files.deleteIfExists(tempFile);

            OcrResponse response = OcrResponse.builder()
                    .title(filename)
                    .description("AI-generated summary based on the extracted contents.")
                    .tags(result.getTags())
                    .keyPoints(result.getKeyPoints())
                    .summary(result.getSummary())
                    .build();

            AiAnalysisOutcome analysis = AiAnalysisOutcome.builder()
                    .summary(result.getSummary())
                    .tags(result.getTags())
                    .keyPoints(result.getKeyPoints())
                    .build();

            // Save to DB
            saveDocumentAsync(filename, result.getExtractedText(), analysis);
            
            // Mark Job as Completed
            ocrJobManager.completeJob(jobId, response);
            
        } catch (Exception e) {
            log.error("Async OCR processing failed for {}: {}", filename, e.getMessage());
            ocrJobManager.failJob(jobId, e.getMessage());
        }
    }

    public OcrResponse processDocument(MultipartFile file) {
        String filename = file.getOriginalFilename();

        try {
            // 1. Save to temp file
            Path tempFile = Files.createTempFile("ocr_", "_" + filename);
            file.transferTo(tempFile.toFile());
            
            // 2. Call persistent Python service (Combined OCR + AI)
            PythonServiceCaller.ProcessResult result = pythonServiceCaller.processDocument(tempFile.toAbsolutePath().toString());
            
            Files.deleteIfExists(tempFile);

            log.info("--- Processing complete for: {} ---", filename);

            // 3. Build response
            OcrResponse response = OcrResponse.builder()
                    .title(filename)
                    .description("AI-generated summary based on the extracted contents.")
                    .tags(result.getTags())
                    .keyPoints(result.getKeyPoints())
                    .summary(result.getSummary())
                    .build();

            // 4. Build analysis outcome for DB save
            AiAnalysisOutcome analysis = AiAnalysisOutcome.builder()
                    .summary(result.getSummary())
                    .tags(result.getTags())
                    .keyPoints(result.getKeyPoints())
                    .build();

            // 5. Attempt DB save asynchronously
            saveDocumentAsync(filename, result.getExtractedText(), analysis);

            return response;

        } catch (Exception e) {
            log.error("OCR processing failed for {}: {}", filename, e.getMessage(), e);
            throw new RuntimeException("Final processing failed: " + e.getMessage(), e);
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
