package com.docusphere.backend.ocr.service;

import com.docusphere.backend.ocr.dto.AiAnalysisOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
@Slf4j
@RequiredArgsConstructor
public class AiAnalysisService {

    private final ObjectMapper objectMapper;

    private final PythonExecutor pythonExecutor;

    @Value("${ocr.analysis.script.path:scripts/analyze_text.py}")
    private String analysisScriptPath;

    public AiAnalysisOutcome analyzeText(String rawText) {
        try {
            java.nio.file.Path tempTextFile = java.nio.file.Files.createTempFile("text_", ".txt");
            java.nio.file.Files.writeString(tempTextFile, rawText);

            String result = pythonExecutor.executeScript(analysisScriptPath, List.of(tempTextFile.toAbsolutePath().toString()));
            
            java.nio.file.Files.deleteIfExists(tempTextFile);

            return parsePythonOutput(result);

        } catch (Exception e) {
            log.error("AI Analysis failed: {}", e.getMessage());
            return AiAnalysisOutcome.builder()
                    .summary("Analysis failed. " + e.getMessage())
                    .tags(List.of("Error"))
                    .keyPoints(List.of("Extraction could not be completed."))
                    .build();
        }
    }


    private AiAnalysisOutcome parsePythonOutput(String output) {
        try {
            // Find the last JSON block in the output (in case there's logging noise)
            int jsonStartIndex = output.lastIndexOf("{");
            if (jsonStartIndex == -1) {
                throw new RuntimeException("No JSON output found from Python script");
            }
            String jsonPart = output.substring(jsonStartIndex);
            return objectMapper.readValue(jsonPart, AiAnalysisOutcome.class);
        } catch (Exception e) {
            log.error("Failed to parse Python JSON output: {}. Raw output: {}", e.getMessage(), output);
            return AiAnalysisOutcome.builder()
                    .summary("Failed to parse analysis results.")
                    .tags(List.of("Error"))
                    .keyPoints(List.of("Check backend logs for details."))
                    .build();
        }
    }
}
