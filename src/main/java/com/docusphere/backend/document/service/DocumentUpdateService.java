package com.docusphere.backend.document.service;

import com.docusphere.backend.document.entity.Document;
import com.docusphere.backend.document.repository.DocumentRepository;
import com.docusphere.backend.upload.service.SupabaseStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentUpdateService {

    private final DocumentRepository documentRepository;
    private final SupabaseStorageService supabaseStorageService;

    @Value("${supabase.bucket.documents}")
    private String bucket;

    public Document saveEditedDocument(UUID documentId, MultipartFile file) throws IOException {
        Document existingDoc = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        // Generate a NEW storageKey to avoid overwriting (Version History requirement)
        String newFileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        String newStorageKey = "versions/" + existingDoc.getId() + "/" + newFileName;

        // Convert MultipartFile to File for SupabaseStorageService
        File tempFile = File.createTempFile("upload-", newFileName);
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(file.getBytes());
        }

        // Upload to Supabase
        String publicUrl = supabaseStorageService.uploadFile(tempFile, bucket, newStorageKey);
        
        // Update document metadata with the NEW version info
        existingDoc.setStorageKey(newStorageKey);
        existingDoc.setFileUrl(publicUrl);
        existingDoc.setName(file.getOriginalFilename());
        existingDoc.setUpdatedAt(LocalDateTime.now());
        
        Document savedDoc = documentRepository.save(existingDoc);
        
        tempFile.delete();
        return savedDoc;
    }
}
