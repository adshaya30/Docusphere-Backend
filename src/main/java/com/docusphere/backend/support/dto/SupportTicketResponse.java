package com.docusphere.backend.support.dto;

import com.docusphere.backend.support.entity.TicketCategory;
import com.docusphere.backend.support.entity.TicketPriority;
import com.docusphere.backend.support.entity.TicketStatus;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicketResponse {
    private Long id;
    private Long userId;
    private String userFullName;
    private String userEmail;
    private String subject;
    private TicketCategory category;
    private TicketPriority priority;
    private String description;
    private TicketStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
