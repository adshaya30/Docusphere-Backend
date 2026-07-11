-- Add team archiving columns
ALTER TABLE team
ADD COLUMN archived_at TIMESTAMP NULL,
ADD COLUMN archived_by BIGINT NULL,
ADD COLUMN archive_reason TEXT NULL;

-- Create index for archived teams queries
CREATE INDEX idx_team_archived_at ON team(archived_at);
