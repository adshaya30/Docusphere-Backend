package com.docusphere.backend.document.storage;

import com.docusphere.backend.Common.exception.FileUploadException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SupabaseFileStorageService implements FileStorageService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service.key}")
    private String serviceKey;

    @Value("${supabase.bucket.documents:documents}")
    private String bucketName;

    @Override
    public String copyFile(String sourcePath, String targetPath) {
        byte[] sourceBytes = loadFile(sourcePath);
        uploadBytes(sourceBytes, targetPath);
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

    private void uploadBytes(byte[] content, String targetPath) {
        String url = buildObjectUrl(targetPath);
        HttpHeaders headers = buildAuthHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.set("x-upsert", "false");
        HttpEntity<byte[]> request = new HttpEntity<>(content, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new FileUploadException("Failed to copy file to storage: " + targetPath);
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
