package com.docusphere.backend.documentStar.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "document_stars",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "document_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentStar {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private Long userId;

    private UUID documentId;

    private LocalDateTime starredAt;

    @PrePersist
    public void onCreate() {
        starredAt = LocalDateTime.now();
    }
}