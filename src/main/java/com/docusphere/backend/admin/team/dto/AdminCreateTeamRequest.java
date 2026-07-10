package com.docusphere.backend.admin.team.dto;

import jakarta.validation.constraints.NotBlank;

import lombok.Data;

import java.util.List;

@Data
public class AdminCreateTeamRequest {
    @NotBlank(message = "Team name is required")
    private String name;

    private String description;

    private Long leaderId;

    private List<AdditionalMember> additionalMembers;

    @Data
    public static class AdditionalMember {
        private Long userId; // Optional if existing user
        private String email; // Required if inviting by email
        private String role; // MEMBER or MANAGER
    }
}
