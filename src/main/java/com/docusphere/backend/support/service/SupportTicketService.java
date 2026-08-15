package com.docusphere.backend.support.service;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.support.dto.*;
import com.docusphere.backend.support.entity.*;
import com.docusphere.backend.support.repository.SupportTicketMessageRepository;
import com.docusphere.backend.support.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupportTicketService {

    private final SupportTicketRepository ticketRepository;
    private final SupportTicketMessageRepository messageRepository;

    @Transactional
    public SupportTicketResponse createTicket(CreateSupportTicketRequest request, User currentUser) {
        SupportTicket ticket = SupportTicket.builder()
                .user(currentUser)
                .subject(request.getSubject())
                .category(request.getCategory())
                .priority(request.getPriority())
                .description(request.getDescription())
                .status(TicketStatus.OPEN)
                .build();

        SupportTicket savedTicket = ticketRepository.save(ticket);

        // Save the first message (the user's description)
        SupportTicketMessage firstMessage = SupportTicketMessage.builder()
                .ticket(savedTicket)
                .sender(currentUser)
                .message(request.getDescription())
                .build();
        messageRepository.save(firstMessage);

        return mapToTicketResponse(savedTicket);
    }

    public List<SupportTicketResponse> getUserTickets(User currentUser, TicketStatus status) {
        List<SupportTicket> tickets;
        if (status != null) {
            tickets = ticketRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(currentUser.getId(), status);
        } else {
            tickets = ticketRepository.findByUserIdOrderByUpdatedAtDesc(currentUser.getId());
        }
        return tickets.stream().map(this::mapToTicketResponse).collect(Collectors.toList());
    }

    public SupportTicketResponse getTicketById(Long id, User currentUser) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new InvalidRequestException("Support ticket not found"));

        validateOwnershipOrAdmin(ticket, currentUser);

        return mapToTicketResponse(ticket);
    }

    public List<SupportTicketMessageResponse> getTicketMessages(Long ticketId, User currentUser) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new InvalidRequestException("Support ticket not found"));

        validateOwnershipOrAdmin(ticket, currentUser);

        List<SupportTicketMessage> messages = messageRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
        return messages.stream().map(this::mapToMessageResponse).collect(Collectors.toList());
    }

    @Transactional
    public SupportTicketMessageResponse sendMessage(Long ticketId, CreateSupportMessageRequest request, User currentUser) {
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new InvalidRequestException("Support ticket not found"));

        validateOwnershipOrAdmin(ticket, currentUser);

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new InvalidRequestException("Cannot send messages to a closed support ticket.");
        }

        SupportTicketMessage message = SupportTicketMessage.builder()
                .ticket(ticket)
                .sender(currentUser)
                .message(request.getMessage())
                .build();

        SupportTicketMessage savedMessage = messageRepository.save(message);

        // Automatically update the updatedAt timestamp of the ticket
        ticket.setStatus(TicketStatus.OPEN); // reopen if closed or set back to open when user posts? Wait, let's keep it as is but touch ticket to update timestamp.
        ticketRepository.save(ticket);

        return mapToMessageResponse(savedMessage);
    }

    // Admin/Support Actions
    public List<SupportTicketResponse> getAllTicketsForAdmin(User admin, TicketStatus status) {
        validateAdminRole(admin);
        List<SupportTicket> tickets;
        if (status != null) {
            tickets = ticketRepository.findAllByStatusOrderByUpdatedAtDesc(status);
        } else {
            tickets = ticketRepository.findAllByOrderByUpdatedAtDesc();
        }
        return tickets.stream().map(this::mapToTicketResponse).collect(Collectors.toList());
    }

    @Transactional
    public SupportTicketResponse updateTicketStatus(Long ticketId, TicketStatus status, User admin) {
        validateAdminRole(admin);
        SupportTicket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new InvalidRequestException("Support ticket not found"));

        ticket.setStatus(status);
        SupportTicket updatedTicket = ticketRepository.save(ticket);

        // Optional: add a system message in the chat that the status was updated
        SupportTicketMessage systemMessage = SupportTicketMessage.builder()
                .ticket(updatedTicket)
                .sender(admin)
                .message("Ticket status updated to " + status.name())
                .build();
        messageRepository.save(systemMessage);

        return mapToTicketResponse(updatedTicket);
    }

    // Helper validations
    private void validateOwnershipOrAdmin(SupportTicket ticket, User currentUser) {
        boolean isAdmin = currentUser.getRole() != null && "ROLE_ADMIN".equals(currentUser.getRole().getName());
        if (!ticket.getUser().getId().equals(currentUser.getId()) && !isAdmin) {
            throw new UnauthorizedAccessException("You are not authorized to access this support ticket.");
        }
    }

    private void validateAdminRole(User admin) {
        boolean isAdmin = admin.getRole() != null && "ROLE_ADMIN".equals(admin.getRole().getName());
        if (!isAdmin) {
            throw new UnauthorizedAccessException("Only admin users can perform this action.");
        }
    }

    // Mappers
    private SupportTicketResponse mapToTicketResponse(SupportTicket ticket) {
        return SupportTicketResponse.builder()
                .id(ticket.getId())
                .userId(ticket.getUser().getId())
                .userFullName(ticket.getUser().getFullName())
                .userEmail(ticket.getUser().getEmail())
                .subject(ticket.getSubject())
                .category(ticket.getCategory())
                .priority(ticket.getPriority())
                .description(ticket.getDescription())
                .status(ticket.getStatus())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .build();
    }

    private SupportTicketMessageResponse mapToMessageResponse(SupportTicketMessage message) {
        return SupportTicketMessageResponse.builder()
                .id(message.getId())
                .ticketId(message.getTicket().getId())
                .senderId(message.getSender().getId())
                .senderFullName(message.getSender().getFullName())
                .senderRole(message.getSender().getRole() != null ? message.getSender().getRole().getName() : "ROLE_USER")
                .message(message.getMessage())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
