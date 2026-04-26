package com.docusphere.backend.upload.service;
import org.springframework.core.io.FileSystemResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service.key}")
    private String serviceKey;

    private final RestTemplate restTemplate = new RestTemplate();

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