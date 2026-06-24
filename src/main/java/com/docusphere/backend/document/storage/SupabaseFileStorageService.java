package com.docusphere.backend.document.storage;

import com.docusphere.backend.Common.exception.FileUploadException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;

/**
 * Unified Supabase storage service.
 * Consolidates upload, download, copy, and delete operations.
 * Replaces duplicate SupabaseStorageService class.
 */
@Service
public class SupabaseFileStorageService implements FileStorageService {

    private RestTemplate restTemplate = new RestTemplate();

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service.key}")
    private String serviceKey;

    @Value("${supabase.bucket.documents:documents}")
    private String bucketName;

    // ──────────────────────────── File Operations ────────────────────────────────

    @Override
    public String copyFile(String sourcePath, String targetPath) {
        byte[] sourceBytes = loadFile(sourcePath);
        upsertBytes(sourceBytes, targetPath);
        return targetPath;
    }

    @Override
    public void deleteFile(String path) {
        String url = buildObjectUrl(path);
        HttpHeaders headers = buildAuthHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.DELETE,
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new FileUploadException("Failed to delete file from storage: " + path);
        }
    }

    @Override
    public byte[] loadFile(String path) {
        String url = buildObjectUrl(path);
        HttpHeaders headers = buildAuthHeaders();
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                request,
                byte[].class
        );

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new FileUploadException("File not found in storage: " + path);
        }

        return response.getBody();
    }

    /**
     * Upload file directly from File object.
     * Used by DocumentUploadService for chunked uploads.
     * Consolidates logic from old SupabaseStorageService.
     */
    public String uploadFile(File file, String storageKey) {
        try {
            String url = buildObjectUrl(storageKey);
            HttpHeaders headers = buildAuthHeaders();
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
                throw new FileUploadException(
                        "Upload failed: " + response.getStatusCode() +
                                " - " + response.getBody()
                );
            }

            return getPublicUrl(storageKey);
        } catch (FileUploadException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new FileUploadException("File upload failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Get public URL for a file in storage.
     * Used to generate accessible links for downloaded files.
     */
    public String getPublicUrl(String storageKey) {
        return supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + storageKey;
    }

    // ------------------------ Helpers -----------------------------------

    private void uploadBytes(byte[] content, String targetPath) {
        upsertBytes(content, targetPath);
    }

    private void upsertBytes(byte[] content, String targetPath) {
        String url = buildObjectUrl(targetPath);
        HttpHeaders headers = buildAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("x-upsert", "true");
        HttpEntity<byte[]> request = new HttpEntity<>(content, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.PUT,
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new FileUploadException("Failed to upload file to storage: " + targetPath);
        }
    }

    private String buildObjectUrl(String storagePath) {
        return supabaseUrl + "/storage/v1/object/" + bucketName + "/" + storagePath;
    }

    private HttpHeaders buildAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(serviceKey);
        return headers;
    }
}
