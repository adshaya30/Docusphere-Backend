package com.docusphere.backend.ocr.controller;

import com.docusphere.backend.ocr.service.OcrService;
import com.docusphere.backend.ocr.service.OcrJobManager;
import com.docusphere.backend.ocr.service.OcrUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
@Lazy
@Slf4j
public class OcrController {
    
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("--- OcrController initialized and mapped to /api/ocr ---");
    }

    private final OcrService ocrService;
    private final OcrJobManager ocrJobManager;
    private final OcrUploadService ocrUploadService;

    @PostMapping("/init-upload")
    public ResponseEntity<Map<String, String>> initUpload(@RequestBody Map<String, String> request) {
        try {
            String fileName = request.get("fileName");
            String fileId = ocrUploadService.initUpload(fileName);
            return ResponseEntity.ok(Map.of("fileId", fileId));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/upload-chunk")
    public ResponseEntity<Void> uploadChunk(
            @RequestParam("fileId") String fileId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("file") MultipartFile file) {
        try {
            ocrUploadService.uploadChunk(fileId, chunkIndex, file);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Chunk upload failed for fileId: {}, chunk: {}. Error: {}", fileId, chunkIndex, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/process-merged")
    public ResponseEntity<Map<String, String>> processMerged(
            @RequestBody Map<String, Object> request) {
        try {
            String fileId = (String) request.get("fileId");
            String fileName = (String) request.get("fileName");
            int totalChunks = (int) request.get("totalChunks");

            java.io.File mergedFile = ocrUploadService.mergeChunks(fileId, fileName, totalChunks);
            
            String jobId = ocrJobManager.createJob(fileName);
            ocrService.processFileAsync(mergedFile, jobId, fileId);

            return ResponseEntity.accepted().body(Map.of("jobId", jobId));
        } catch (Exception e) {
            log.error("Failed to merge and process OCR file: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

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
