package com.docusphere.ocr.controller;

import com.docusphere.ocr.dto.OcrResponse;
import com.docusphere.ocr.service.OcrService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ocr")
@RequiredArgsConstructor
public class OcrController {

    private final OcrService ocrService;

    @PostMapping("/upload")
    public ResponseEntity<OcrResponse> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        OcrResponse response = ocrService.processDocument(file);
        return ResponseEntity.ok(response);
    }
}
