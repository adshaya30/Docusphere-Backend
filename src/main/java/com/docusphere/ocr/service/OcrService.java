package com.docusphere.ocr.service;

import com.docusphere.ocr.dto.AiAnalysisOutcome;
import com.docusphere.ocr.dto.OcrResponse;
import com.docusphere.ocr.model.Document;
import com.docusphere.ocr.repository.OcrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;



@Service
@RequiredArgsConstructor
public class OcrService {

    private final OcrRepository ocrRepository;
    private final OcrExtractionService ocrExtractionService;
    private final AiAnalysisService aiAnalysisService;

    public OcrResponse processDocument(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String extractedText = "";

        try {
            // 1. Save and OCR
            java.nio.file.Path tempFile = java.nio.file.Files.createTempFile("ocr_", "_" + filename);
            java.io.File targetFile = tempFile.toFile();
            if (targetFile == null) {
                throw new java.io.IOException("Failed to create temporary file");
            }
            file.transferTo(targetFile);
            extractedText = ocrExtractionService.extractText(tempFile.toAbsolutePath().toString());
            java.nio.file.Files.deleteIfExists(tempFile);

            // 2. AI Summarization & Tagging
            AiAnalysisOutcome analysis = aiAnalysisService.analyzeText(extractedText);

            // 3. Save to Database
            Document document = Document.builder()
                    .filename(filename)
                    .rawExtractedText(extractedText)
                    .aiSummary(analysis.getSummary())
                    .tags(analysis.getTags())
                    .build();

            @SuppressWarnings("null")
            Document savedDoc = ocrRepository.save(document);

            return OcrResponse.builder()
                    .title(savedDoc.getFilename())
                    .description("AI-generated summary based on the extracted contents.")
                    .tags(savedDoc.getTags())
                    .summary(savedDoc.getAiSummary())
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Final processing failed", e);
        }
    }
}
