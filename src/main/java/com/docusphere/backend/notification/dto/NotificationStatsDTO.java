package com.docusphere.backend.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for notification statistics
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationStatsDTO {
    private long unreadCount;
    private long readCount;
    private long totalCount;
}
