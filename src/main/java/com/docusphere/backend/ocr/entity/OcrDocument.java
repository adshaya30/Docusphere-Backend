package com.docusphere.backend.ocr.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "ocrdocument")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;

    @Column(columnDefinition = "TEXT")
    private String rawExtractedText;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    @ElementCollection
    @CollectionTable(name = "ocrdocument_tags", joinColumns = @JoinColumn(name = "document_id"))
    @Column(name = "tag")
    private List<String> tags;

    @ElementCollection
    @CollectionTable(name = "ocrdocument_keypoints", joinColumns = @JoinColumn(name = "document_id"))
    @Column(name = "key_point", columnDefinition = "TEXT")
    private List<String> keyPoints;

    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
