package com.docusphere.backend.ocr.repository;

import com.docusphere.backend.ocr.entity.OcrDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OcrRepository extends JpaRepository<OcrDocument, Long> {
}
