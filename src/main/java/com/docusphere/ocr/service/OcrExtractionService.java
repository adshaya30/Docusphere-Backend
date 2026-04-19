package com.docusphere.ocr.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class OcrExtractionService {

    @Value("${ocr.python.script.path:scripts/paddle_ocr.py}")
    private String pythonScriptPath;

    @Value("${ocr.python.executable:python}")
    private String pythonExecutable;

    public String extractText(String filePath) {
        StringBuilder output = new StringBuilder();
        try {
            List<String> command = new ArrayList<>();
            command.add(pythonExecutable);
            command.add(pythonScriptPath);
            command.add(filePath);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.environment().put("FLAGS_use_mkldnn", "0");
            processBuilder.environment().put("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True");
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Python OCR script failed with exit code: {}. Output:\n{}", exitCode, output.toString());
                throw new RuntimeException("OCR processing failed. Exit code: " + exitCode);
            }

            return output.toString().trim();

        } catch (Exception e) {
            log.error("Error executing Python OCR script: {}", e.getMessage());
            throw new RuntimeException("Error during OCR extraction", e);
        }
    }
}
