package com.docusphere.backend.authentication.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class SupabaseProfileStorageService {
    private static final Logger log = LoggerFactory.getLogger(SupabaseProfileStorageService.class);

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service.key}")
    private String serviceKey;

    @Value("${supabase.bucket.profile:profile-pictures}")
    private String profileBucket;

    public String uploadProfilePicture(MultipartFile file, Long userId) throws Exception {
        try {
            String extension = getFileExtension(file.getOriginalFilename());
            String filename = "user-" + userId + "-" + UUID.randomUUID() + extension;

            String uploadUrl = supabaseUrl + "/storage/v1/object/" + profileBucket + "/" + filename;
            log.debug("Uploading profile picture to: {}", uploadUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + serviceKey);
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            HttpEntity<byte[]> entity = new HttpEntity<>(file.getBytes(), headers);

            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String publicUrl = supabaseUrl + "/storage/v1/object/public/" + profileBucket + "/" + filename;
                log.info("Profile picture uploaded successfully: {}", publicUrl);
                return publicUrl;
            } else {
                log.error("Failed to upload image. Status: {}", response.getStatusCode());
                throw new RuntimeException("Failed to upload image: " + response.getStatusCode());
            }
        } catch (RestClientException e) {
            log.error("Network error while uploading to Supabase. URL: {}, Error: {}", supabaseUrl, e.getMessage());
            log.error("Possible causes: 1) Internet connection down, 2) Firewall blocking Supabase, 3) Supabase service down, 4) Invalid credentials");
            throw new RuntimeException("Failed to upload image: Connection error to Supabase. " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error while uploading profile picture", e);
            throw new RuntimeException("Failed to upload image: " + e.getMessage(), e);
        }
    }

    public void deleteProfilePicture(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;

        try {
            String filename = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
            String deleteUrl = supabaseUrl + "/storage/v1/object/" + profileBucket + "/" + filename;

            log.debug("Deleting profile picture from: {}", deleteUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + serviceKey);

            RestTemplate restTemplate = new RestTemplate();
            restTemplate.exchange(deleteUrl, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
            log.info("Profile picture deleted successfully: {}", filename);
        } catch (RestClientException e) {
            log.warn("Network error while deleting image from Supabase: {}", e.getMessage());
            log.warn("Possible causes: 1) Internet connection down, 2) Firewall blocking Supabase, 3) Supabase service down, 4) Invalid credentials");
        } catch (Exception e) {
            log.warn("Failed to delete image from Supabase: {}", e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf("."));
    }
}
