package com.docusphere.backend.authentication.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
public class SupabaseProfileStorageService {
    @Value("${supabase.url}")
    private String supabaseUrl;

    // Use the service key configured in application.properties (supabase.service.key)
    @Value("${supabase.service.key}")
    private String serviceKey;

    // Follows naming style: supabase.bucket.<purpose>
    @Value("${supabase.bucket.profile:profile-pictures}")
    private String profileBucket;
    /**
     * Upload profile picture to Supabase and return public URL
     */
    public String uploadProfilePicture(MultipartFile file, Long userId) throws Exception {
        String extension = getFileExtension(file.getOriginalFilename());
        String filename = "user-" + userId + "-" + UUID.randomUUID() + extension;

        String uploadUrl = supabaseUrl + "/storage/v1/object/" + profileBucket + "/" + filename;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceKey);
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        HttpEntity<byte[]> entity = new HttpEntity<>(file.getBytes(), headers);

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange(uploadUrl, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful()) {
            return supabaseUrl + "/storage/v1/object/public/" + profileBucket + "/" + filename;
        } else {
            throw new RuntimeException("Failed to upload image");
        }
    }

    /**
     * Delete profile picture from Supabase
     */
    public void deleteProfilePicture(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) return;

        try {
            String filename = imageUrl.substring(imageUrl.lastIndexOf("/") + 1);
            String deleteUrl = supabaseUrl + "/storage/v1/object/" + profileBucket + "/" + filename;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + serviceKey);

            RestTemplate restTemplate = new RestTemplate();
            restTemplate.exchange(deleteUrl, HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
        } catch (Exception e) {
            System.err.println("Failed to delete image: " + e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf("."));
    }
}
