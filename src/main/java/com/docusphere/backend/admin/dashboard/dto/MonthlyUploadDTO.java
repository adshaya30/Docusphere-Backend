package com.docusphere.backend.admin.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyUploadDTO {
    private Integer month;
   private Long monthlyUploads;
}