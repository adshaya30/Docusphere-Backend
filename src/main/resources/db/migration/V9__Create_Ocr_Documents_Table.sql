-- Create main OCR document table
CREATE TABLE IF NOT EXISTS ocrdocument (
    id BIGSERIAL PRIMARY KEY,
    filename VARCHAR(255),
    raw_extracted_text TEXT,
    ai_summary TEXT,
    uploaded_at TIMESTAMP WITHOUT TIME ZONE
);

-- Create table for document tags (one-to-many)
CREATE TABLE IF NOT EXISTS ocrdocument_tags (
    document_id BIGINT NOT NULL,
    tag VARCHAR(255),
    CONSTRAINT fk_ocr_document_tags FOREIGN KEY (document_id) REFERENCES ocrdocument(id) ON DELETE CASCADE
);

-- Create table for document key points (one-to-many)
CREATE TABLE IF NOT EXISTS ocrdocument_keypoints (
    document_id BIGINT NOT NULL,
    key_point TEXT,
    CONSTRAINT fk_ocr_document_keypoints FOREIGN KEY (document_id) REFERENCES ocrdocument(id) ON DELETE CASCADE
);
