-- Notification table migration
-- Created: 2026-05-24
-- Description: Create notification table for real-time notifications system

CREATE TABLE IF NOT EXISTS notification (
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT,
    metadata TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    audience VARCHAR(20) NOT NULL DEFAULT 'USER',
    related_entity_id UUID,
    related_entity_type VARCHAR(50),
    action_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP,
    archived_at TIMESTAMP,
    
    PRIMARY KEY (id),
    FOREIGN KEY (user_id) REFERENCES "user"(id) ON DELETE CASCADE
);

-- Create indexes for performance
CREATE INDEX idx_notification_user_id ON notification(user_id);
CREATE INDEX idx_notification_status ON notification(status);
CREATE INDEX idx_notification_created_at ON notification(created_at DESC);
CREATE INDEX idx_notification_user_status ON notification(user_id, status);
CREATE INDEX IF NOT EXISTS idx_notification_user_audience ON notification(user_id, audience);
CREATE INDEX IF NOT EXISTS idx_notification_user_audience_status ON notification(user_id, audience, status);
CREATE INDEX idx_notification_type ON notification(type);
CREATE INDEX idx_notification_read_at ON notification(read_at);

-- Add comment to table
COMMENT ON TABLE notification IS 'Stores real-time notifications for users';
COMMENT ON COLUMN notification.id IS 'Unique notification identifier';
COMMENT ON COLUMN notification.user_id IS 'ID of the user receiving the notification';
COMMENT ON COLUMN notification.type IS 'Type of notification (TEAM_INVITATION, DOCUMENT_SHARED, etc.)';
COMMENT ON COLUMN notification.title IS 'Notification title';
COMMENT ON COLUMN notification.message IS 'Notification message content';
COMMENT ON COLUMN notification.metadata IS 'JSON metadata for additional data';
COMMENT ON COLUMN notification.status IS 'Notification status (UNREAD, READ, ARCHIVED)';
COMMENT ON COLUMN notification.related_entity_id IS 'ID of related entity (document, team, action)';
COMMENT ON COLUMN notification.related_entity_type IS 'Type of related entity';
COMMENT ON COLUMN notification.action_url IS 'URL for frontend navigation on notification click';
COMMENT ON COLUMN notification.created_at IS 'Notification creation timestamp';
COMMENT ON COLUMN notification.read_at IS 'Timestamp when notification was marked as read';
COMMENT ON COLUMN notification.archived_at IS 'Timestamp when notification was archived';
