package com.docusphere.backend.notification.dto;

import com.docusphere.backend.notification.entity.NotificationAudience;
import com.docusphere.backend.notification.entity.NotificationStatus;
import com.docusphere.backend.notification.entity.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for Notification response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {
    
    private UUID id;
    private UUID userId;
    private NotificationType type;
    private NotificationAudience audience;
    private String title;
    private String message;
    private String metadata;
    private NotificationStatus status;
    private UUID relatedEntityId;
    private String relatedEntityType;
    private String actionUrl;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private LocalDateTime archivedAt;
    
    @JsonProperty("isUnread")
    public boolean isUnread() {
        return status == NotificationStatus.UNREAD;
    }
}
