package com.docusphere.backend.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for paginated notification response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPageDTO {
    private List<NotificationDTO> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
}
