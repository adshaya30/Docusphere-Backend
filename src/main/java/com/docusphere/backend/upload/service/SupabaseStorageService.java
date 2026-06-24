package com.docusphere.backend.upload.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * @deprecated This class is no longer used. All storage operations have been consolidated
 * into SupabaseFileStorageService in the document.storage package.
 * 
 * Use: com.docusphere.backend.document.storage.SupabaseFileStorageService instead.
 * 
 * Migration: Replace injections of SupabaseStorageService with FileStorageService.
 * All methods are available in SupabaseFileStorageService:
 * - uploadFile(File, storageKey) - replaces uploadFile(File, bucket, filePath)
 * - getPublicUrl(storageKey) - returns public URL
 * - copyFile(sourcePath, targetPath)
 * - deleteFile(path)
 * - loadFile(path)
 */
@Deprecated(since = "1.0", forRemoval = true)
@Service
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service.key}")
    private String serviceKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * @deprecated Use SupabaseFileStorageService.uploadFile(File, storageKey) instead
     */
    @Deprecated(since = "1.0", forRemoval = true)
    public String uploadFile(java.io.File file, String bucket, String filePath) {

        String url = supabaseUrl + "/storage/v1/object/" + bucket + "/" + filePath;

        String trimmedKey = (serviceKey != null) ? serviceKey.trim() : "";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + trimmedKey);
        headers.set("apikey", trimmedKey);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        FileSystemResource resource = new FileSystemResource(file);
        HttpEntity<FileSystemResource> request = new HttpEntity<>(resource, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException(
                    "Upload failed: " + response.getStatusCode() +
                            " - " + response.getBody()
            );
        }

        // PUBLIC URL
        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + filePath;
    }
}