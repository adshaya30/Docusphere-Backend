package com.docusphere.backend.support.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportTicketMessageResponse {
    private Long id;
    private Long ticketId;
    private Long senderId;
    private String senderFullName;
    private String senderRole;
    private String message;
    private LocalDateTime createdAt;
}
