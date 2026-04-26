package com.docusphere.backend.ocr.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
@Slf4j
public class PythonServiceCaller {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String PYTHON_SERVICE_EXTRACT_URL = "http://localhost:5000/extract";
    private final String PYTHON_SERVICE_ANALYZE_URL = "http://localhost:5000/analyze";
    private final String PYTHON_SERVICE_URL = "http://localhost:5000/process";

    public String extractText(String imagePath) {
        try {
            Map<String, String> request = Map.of("image_path", imagePath);
            Map<String, String> result = restTemplate.postForObject(PYTHON_SERVICE_EXTRACT_URL, request, Map.class);
            return result != null ? result.get("extractedText") : "";
        } catch (Exception e) {
            log.error("Extraction failed: {}", e.getMessage());
            return "";
        }
    }

    public ProcessResult analyzeText(String text) {
        try {
            Map<String, String> request = Map.of("text", text);
            return restTemplate.postForObject(PYTHON_SERVICE_ANALYZE_URL, request, ProcessResult.class);
        } catch (Exception e) {
            log.error("Analysis failed: {}", e.getMessage());
            return new ProcessResult();
        }
    }

    public ProcessResult processDocument(String imagePath) {
        try {
            Map<String, String> request = Map.of("image_path", imagePath);
            return restTemplate.postForObject(PYTHON_SERVICE_URL, request, ProcessResult.class);
        } catch (Exception e) {
            log.error("Failed to call Python OCR service: {}", e.getMessage());
            throw new RuntimeException("Python OCR service communication error", e);
        }
    }

    @Data
    public static class ProcessResult {
        private String extractedText;
        private String summary;
        private java.util.List<String> tags;
        private java.util.List<String> keyPoints;
    }
}
