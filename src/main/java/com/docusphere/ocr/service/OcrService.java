package com.docusphere.ocr.service;

import com.docusphere.ocr.dto.AiAnalysisOutcome;
import com.docusphere.ocr.dto.OcrResponse;
import com.docusphere.ocr.entity.OcrDocument;
import com.docusphere.ocr.repository.OcrRepository;
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
    private final OcrExtractionService ocrExtractionService;
    private final AiAnalysisService aiAnalysisService;

    public OcrResponse processDocument(MultipartFile file) {
        String filename = file.getOriginalFilename();

        try {
            // 1. Save to temp file and extract text via Python/PaddleOCR
            Path tempFile = Files.createTempFile("ocr_", "_" + filename);
            file.transferTo(tempFile.toFile());
            String extractedText = ocrExtractionService.extractText(tempFile.toAbsolutePath().toString());
            Files.deleteIfExists(tempFile);

            log.info("--- OCR extraction complete for: {} ---", filename);

            // 2. AI Summarization & Tagging
            AiAnalysisOutcome analysis = aiAnalysisService.analyzeText(extractedText);

            // 3. Build response FIRST — return to user regardless of DB state
            OcrResponse response = OcrResponse.builder()
                    .title(filename)
                    .description("AI-generated summary based on the extracted contents.")
                    .tags(analysis.getTags())
                    .keyPoints(analysis.getKeyPoints())
                    .summary(analysis.getSummary())
                    .build();

            // 4. Attempt DB save asynchronously (best-effort — does not block response)
            saveDocumentAsync(filename, extractedText, analysis);

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
