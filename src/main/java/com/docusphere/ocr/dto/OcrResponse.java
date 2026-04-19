package com.docusphere.ocr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResponse {
    private String title;
    private String description;
    private List<String> tags;
    private List<String> keyPoints;
    private String summary;
}
