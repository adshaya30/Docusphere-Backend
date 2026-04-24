-- Repair the documents table to match the new Document entity structure
ALTER TABLE documents ADD COLUMN IF NOT EXISTS storage_path TEXT;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS storage_key TEXT;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS file_url TEXT;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'COMPLETED';

-- Backfill existing rows with empty strings or default values to avoid NULL constraint violations
UPDATE documents SET storage_path = '' WHERE storage_path IS NULL;
UPDATE documents SET storage_key = '' WHERE storage_key IS NULL;
UPDATE documents SET file_url = '' WHERE file_url IS NULL;
UPDATE documents SET status = 'COMPLETED' WHERE status IS NULL;

-- Now safe to set NOT NULL
ALTER TABLE documents ALTER COLUMN storage_path SET NOT NULL;
ALTER TABLE documents ALTER COLUMN storage_key SET NOT NULL;
ALTER TABLE documents ALTER COLUMN file_url SET NOT NULL;
ALTER TABLE documents ALTER COLUMN status SET NOT NULL;
