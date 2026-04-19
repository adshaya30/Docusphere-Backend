package com.docusphere.ocr.service;

import com.docusphere.ocr.dto.AiAnalysisOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AiAnalysisService {

    @Value("${ocr.python.executable:python}")
    private String pythonExecutable;

    @Value("${ocr.analysis.script.path:scripts/analyze_text.py}")
    private String analysisScriptPath;

    public AiAnalysisOutcome analyzeText(String rawText) {
        StringBuilder output = new StringBuilder();
        try {
            java.nio.file.Path tempTextFile = java.nio.file.Files.createTempFile("text_", ".txt");
            java.nio.file.Files.writeString(tempTextFile, rawText);

            ProcessBuilder pb = new ProcessBuilder(
                    pythonExecutable, 
                    analysisScriptPath, 
                    tempTextFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            process.waitFor();
            java.nio.file.Files.deleteIfExists(tempTextFile);

            String result = output.toString().trim();
            return parsePythonOutput(result);

        } catch (Exception e) {
            log.error("AI Analysis failed: {}", e.getMessage());
            return AiAnalysisOutcome.builder()
                    .summary("Analysis failed. Using raw text.")
                    .tags(List.of("Error"))
                    .build();
        }
    }

    private AiAnalysisOutcome parsePythonOutput(String output) {
        String[] lines = output.split("\n");
        // Looking for the last two lines as output
        String summary = "No summary generated.";
        List<String> tags = List.of("Document");

        if (lines.length >= 2) {
            summary = lines[lines.length - 2];
            tags = Arrays.asList(lines[lines.length - 1].split(","))
                    .stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        return new AiAnalysisOutcome(summary, tags);
    }
}
