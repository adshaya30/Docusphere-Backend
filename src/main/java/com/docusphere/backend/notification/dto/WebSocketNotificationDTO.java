package com.docusphere.backend.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for WebSocket notification message
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebSocketNotificationDTO {
    
    private String id;
    private String audience;
    private String type;
    private String title;
    private String message;
    private String status;
    private String relatedEntityId;
    private String relatedEntityType;
    private String actionUrl;
    private String createdAt;
    private String metadata;
    
    @JsonProperty("isUnread")
    private boolean unread;
}
