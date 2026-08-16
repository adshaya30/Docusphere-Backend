package com.docusphere.backend.support.dto;

import com.docusphere.backend.support.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSupportTicketRequest {

    @NotNull(message = "Status is required")
    private TicketStatus status;
}
