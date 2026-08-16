package com.docusphere.backend.support.controller;

import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.authentication.repository.UserRepository;
import com.docusphere.backend.support.dto.*;
import com.docusphere.backend.support.entity.TicketStatus;
import com.docusphere.backend.support.service.SupportTicketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/support/tickets")
@RequiredArgsConstructor
public class SupportTicketController {

    private final SupportTicketService ticketService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<SupportTicketResponse> createTicket(@Valid @RequestBody CreateSupportTicketRequest request) {
        User currentUser = getCurrentUser();
        SupportTicketResponse response = ticketService.createTicket(request, currentUser);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<SupportTicketResponse>> getUserTickets(@RequestParam(required = false) TicketStatus status) {
        User currentUser = getCurrentUser();
        List<SupportTicketResponse> response = ticketService.getUserTickets(currentUser, status);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<SupportTicketResponse> getTicketById(@PathVariable Long ticketId) {
        User currentUser = getCurrentUser();
        SupportTicketResponse response = ticketService.getTicketById(ticketId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{ticketId}/messages")
    public ResponseEntity<List<SupportTicketMessageResponse>> getTicketMessages(@PathVariable Long ticketId) {
        User currentUser = getCurrentUser();
        List<SupportTicketMessageResponse> response = ticketService.getTicketMessages(ticketId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{ticketId}/messages")
    public ResponseEntity<SupportTicketMessageResponse> sendMessage(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateSupportMessageRequest request) {
        User currentUser = getCurrentUser();
        SupportTicketMessageResponse response = ticketService.sendMessage(ticketId, request, currentUser);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedAccessException("Authentication is required");
        }
        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedAccessException("User not found or not authenticated"));
    }
}
