package com.docusphere.backend.ocr.controller;

import com.docusphere.backend.ocr.service.OcrService;
import com.docusphere.backend.ocr.service.OcrJobManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
@Slf4j
public class OcrController {
    
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("--- OcrController initialized and mapped to /api/ocr ---");
    }

    private final OcrService ocrService;
    private final OcrJobManager ocrJobManager;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        String jobId = ocrJobManager.createJob(file.getOriginalFilename());
        ocrService.processDocumentAsync(file, jobId);
        
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<OcrJobManager.JobStatus> getStatus(@PathVariable String jobId) {
        OcrJobManager.JobStatus status = ocrJobManager.getJobStatus(jobId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}
