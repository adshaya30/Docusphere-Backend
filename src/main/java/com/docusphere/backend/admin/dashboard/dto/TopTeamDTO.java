package com.docusphere.backend.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TopTeamDTO {
    private String teamName;
    private Long documentCount;
    private Double activityPercentage;
}
