package com.docusphere.backend.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensures notification schema exists when Flyway is disabled (spring.flyway.enabled=false).
 * Adds the {@code audience} column required for user vs admin notification separation.
 */
@Component
@Order(1)
@Slf4j
public class NotificationSchemaInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public NotificationSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("""
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
                    PRIMARY KEY (id)
                )
                """);

            jdbcTemplate.execute("""
                ALTER TABLE notification
                ADD COLUMN IF NOT EXISTS audience VARCHAR(20) NOT NULL DEFAULT 'USER'
                """);

            jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_notification_user_id ON notification(user_id)
                """);
            jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_notification_user_audience
                ON notification(user_id, audience)
                """);
            jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_notification_user_audience_status
                ON notification(user_id, audience, status)
                """);

            log.info("Notification schema verified (audience column present)");
        } catch (Exception e) {
            log.error("Failed to initialize notification schema", e);
            throw new IllegalStateException("Notification schema initialization failed", e);
        }
    }
}
