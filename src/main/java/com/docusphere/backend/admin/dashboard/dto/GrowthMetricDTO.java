package com.docusphere.backend.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrowthMetricDTO {
    private Double documentGrowth;
    private Double userGrowth;
    private Double teamGrowth;
    private String status; // "up", "down", "stable"
}
