package com.docusphere.backend.team.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserStatusDto {
    private Long userId;
    private UUID teamId;
    private String status; // ACTIVE or INACTIVE
}
