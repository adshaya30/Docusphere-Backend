package com.docusphere.backend.support.controller;

import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.support.dto.*;
import com.docusphere.backend.support.entity.TicketStatus;
import com.docusphere.backend.support.service.SupportTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/support/tickets")
@RequiredArgsConstructor
public class AdminSupportTicketController {

    private final SupportTicketService ticketService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<SupportTicketResponse>> getAllTickets(@RequestParam(required = false) TicketStatus status) {
        User currentAdmin = getCurrentAdmin();
        List<SupportTicketResponse> response = ticketService.getAllTicketsForAdmin(currentAdmin, status);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{ticketId}")
    public ResponseEntity<SupportTicketResponse> updateTicketStatus(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateSupportTicketRequest request) {
        User currentAdmin = getCurrentAdmin();
        SupportTicketResponse response = ticketService.updateTicketStatus(ticketId, request.getStatus(), currentAdmin);
        return ResponseEntity.ok(response);
    }

    private User getCurrentAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedAccessException("Authentication is required");
        }
        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedAccessException("User not found or not authenticated"));

        boolean isAdmin = user.getRole() != null && "ROLE_ADMIN".equals(user.getRole().getName());
        if (!isAdmin) {
            throw new UnauthorizedAccessException("Only admin users can perform this action");
        }
        return user;
    }
}
