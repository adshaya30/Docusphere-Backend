package com.docusphere.ocr.repository;

import com.docusphere.ocr.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OcrRepository extends JpaRepository<Document, Long> {
}
