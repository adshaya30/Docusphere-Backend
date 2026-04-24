CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY,
    file_id VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    team_id UUID,
    storage_path TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    secured BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_documents_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS document_stars (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    document_id UUID NOT NULL,
    starred_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_document UNIQUE (user_id, document_id),
    CONSTRAINT fk_document_stars_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE CASCADE
);

CREATE INDEX idx_user ON document_stars(user_id);
CREATE INDEX idx_document ON document_stars(document_id);

