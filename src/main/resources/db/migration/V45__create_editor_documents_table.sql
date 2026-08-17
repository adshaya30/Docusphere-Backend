-- Create editor_documents table
CREATE TABLE IF NOT EXISTS editor_documents (
    id UUID PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    storage_path TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_editor_documents_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Index on owner_id for optimized listing queries
CREATE INDEX IF NOT EXISTS idx_editor_documents_owner ON editor_documents(owner_id);
