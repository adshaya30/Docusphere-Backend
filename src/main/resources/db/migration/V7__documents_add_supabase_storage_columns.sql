ALTER TABLE documents
ADD COLUMN IF NOT EXISTS storage_key TEXT;

ALTER TABLE documents
ADD COLUMN IF NOT EXISTS file_url TEXT;

ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS last_accessed_at TIMESTAMP;

-- safe backfill
UPDATE documents
SET storage_key = COALESCE(storage_key, storage_path),
    file_url = COALESCE(file_url, '')
WHERE storage_key IS NULL OR storage_key = '';