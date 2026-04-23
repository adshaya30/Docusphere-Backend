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
    private final String PYTHON_SERVICE_URL = "http://localhost:5000/process";

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
