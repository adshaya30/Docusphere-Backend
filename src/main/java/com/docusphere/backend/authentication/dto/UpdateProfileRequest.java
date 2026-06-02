package com.docusphere.backend.authentication.dto;

import lombok.*;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class UpdateProfileRequest {
    private String fullName;                    // Optional

    private MultipartFile profilePicture;       // Optional

    private boolean removeProfilePicture = false; // To delete profile picture
}
