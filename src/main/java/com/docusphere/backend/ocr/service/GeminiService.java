package com.docusphere.backend.ocr.service;

import com.docusphere.backend.ocr.dto.AiAnalysisOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.context.annotation.Lazy;

import java.util.*;

@Service
@Lazy
@Slf4j
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.timeout.connection:15000}")
    private int connectionTimeout;

    @Value("${gemini.timeout.read:60000}")
    private int readTimeout;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public GeminiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectionTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public AiAnalysisOutcome summarizeText(String cleanedText) {
        log.info("Gemini Analysis started using model: gemini-2.5-flash");
        long startTime = System.currentTimeMillis();

        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.startsWith("${")) {
            log.error("Gemini API key is missing or unconfigured. Returning fallback.");
            return getFallbackOutcome();
        }

        if (cleanedText == null || cleanedText.trim().isEmpty()) {
            log.warn("Extracted text is empty. Returning default empty analysis.");
            return AiAnalysisOutcome.builder()
                    .summary("No content available to summarize.")
                    .keyPoints(Collections.emptyList())
                    .tags(List.of("Empty"))
                    .build();
        }

        // Configure request timeouts dynamically in case they were updated
        SimpleClientHttpRequestFactory rf = (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
        rf.setConnectTimeout(connectionTimeout);
        rf.setReadTimeout(readTimeout);

        int attempt = 0;
        Exception lastException = null;

        while (attempt < 2) {
            attempt++;
            try {
                // Construct payload dynamically using Maps
                Map<String, Object> requestBody = new HashMap<>();

                // Contents
                Map<String, Object> part = new HashMap<>();
                part.put("text", "Analyze this text:\n\n" + cleanedText);
                Map<String, Object> content = new HashMap<>();
                content.put("parts", List.of(part));
                requestBody.put("contents", List.of(content));

                // System Instruction
                Map<String, Object> sysInstructionPart = new HashMap<>();
                sysInstructionPart.put("text", "You are a professional document analysis system. You must analyze the text from a document and return a JSON object with: 'summary' (a brief 2-3 sentence summary of the document), 'keyPoints' (a JSON array of key takeaways as strings, extract all key points without any count limit), and 'tags' (a JSON array of 3-5 keywords/tags for categorization).");
                Map<String, Object> systemInstruction = new HashMap<>();
                systemInstruction.put("parts", List.of(sysInstructionPart));
                requestBody.put("systemInstruction", systemInstruction);

                // Generation Config with schema constraints
                Map<String, Object> generationConfig = new HashMap<>();
                generationConfig.put("responseMimeType", "application/json");

                Map<String, Object> schema = new HashMap<>();
                schema.put("type", "OBJECT");
                Map<String, Object> properties = new HashMap<>();
                properties.put("summary", Map.of("type", "STRING"));
                properties.put("keyPoints", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")));
                properties.put("tags", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")));
                schema.put("properties", properties);
                schema.put("required", List.of("summary", "keyPoints", "tags"));
                generationConfig.put("responseSchema", schema);

                requestBody.put("generationConfig", generationConfig);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                log.info("Sending request to Gemini API (Attempt {})...", attempt);
                
                String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;
                
                ResponseEntity<String> responseEntity = restTemplate.postForEntity(
                        url,
                        entity,
                        String.class
                );

                if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null) {
                    String body = responseEntity.getBody();
                    log.info("Gemini API Complete Response Body: {}", body);
                    
                    Map<String, Object> responseMap = objectMapper.readValue(body, Map.class);
                    List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseMap.get("candidates");
                    
                    if (candidates != null && !candidates.isEmpty()) {
                        Map<String, Object> candidate = candidates.get(0);
                        Map<String, Object> contentMap = (Map<String, Object>) candidate.get("content");
                        if (contentMap != null) {
                            List<Map<String, Object>> parts = (List<Map<String, Object>>) contentMap.get("parts");
                            if (parts != null && !parts.isEmpty()) {
                                String responseText = (String) parts.get(0).get("text");
                                if (responseText != null) {
                                    // Parse the structured JSON returned by Gemini
                                    AiAnalysisOutcome outcome = objectMapper.readValue(responseText, AiAnalysisOutcome.class);
                                    
                                    // Ensure lists are not null
                                    if (outcome.getKeyPoints() == null) outcome.setKeyPoints(Collections.emptyList());
                                    if (outcome.getTags() == null) outcome.setTags(Collections.emptyList());
                                    
                                    long duration = System.currentTimeMillis() - startTime;
                                    log.info("Gemini Analysis completed successfully in {} ms.", duration);
                                    return outcome;
                                }
                            }
                        }
                    }
                }
                throw new RuntimeException("Empty or invalid response structure from Gemini API");

            } catch (HttpStatusCodeException e) {
                lastException = e;
                log.error("Gemini API HTTP Error (Attempt {}): Status Code = {}, Response Body = {}", 
                        attempt, e.getStatusCode(), e.getResponseBodyAsString());
                if (attempt < 2) {
                    log.info("Retrying Gemini Analysis...");
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("Gemini Analysis attempt {} failed: {}", attempt, e.getMessage(), e);
                if (attempt < 2) {
                    log.info("Retrying Gemini Analysis...");
                }
            }
        }

        log.error("Gemini Analysis failed after all attempts. Falling back to default values. Error: {}", 
                lastException != null ? lastException.getMessage() : "Unknown error");
        return getFallbackOutcome();
    }

    private AiAnalysisOutcome getFallbackOutcome() {
        return AiAnalysisOutcome.builder()
                .summary("AI summarization unavailable")
                .keyPoints(Collections.emptyList())
                .tags(List.of("Document"))
                .build();
    }
}
