package com.docusphere.backend.support.service;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.authentication.entity.Role;
import com.docusphere.backend.authentication.entity.User;
import com.docusphere.backend.support.dto.*;
import com.docusphere.backend.support.entity.*;
import com.docusphere.backend.support.repository.SupportTicketMessageRepository;
import com.docusphere.backend.support.repository.SupportTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceTest {

    @Mock
    private SupportTicketRepository ticketRepository;

    @Mock
    private SupportTicketMessageRepository messageRepository;

    private SupportTicketService service;

    private User user;
    private User admin;
    private SupportTicket ticket;

    @BeforeEach
    void setUp() {
        service = new SupportTicketService(ticketRepository, messageRepository);

        Role userRole = new Role(1L, "ROLE_USER");
        Role adminRole = new Role(2L, "ROLE_ADMIN");

        user = new User(1L, "John Doe", "john@example.com", "password", true, userRole, null, null);
        admin = new User(2L, "Admin User", "admin@example.com", "password", true, adminRole, null, null);

        ticket = SupportTicket.builder()
                .id(100L)
                .user(user)
                .subject("Test Ticket")
                .category(TicketCategory.OCR)
                .priority(TicketPriority.HIGH)
                .description("OCR fails to start")
                .status(TicketStatus.OPEN)
                .build();
    }

    @Test
    void createTicket_shouldSaveTicketAndFirstMessage() {
        CreateSupportTicketRequest request = CreateSupportTicketRequest.builder()
                .subject("Test Ticket")
                .category(TicketCategory.OCR)
                .priority(TicketPriority.HIGH)
                .description("OCR fails to start")
                .build();

        when(ticketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        SupportTicketResponse response = service.createTicket(request, user);

        assertNotNull(response);
        assertEquals("Test Ticket", response.getSubject());
        verify(ticketRepository, times(1)).save(any(SupportTicket.class));
        verify(messageRepository, times(1)).save(any(SupportTicketMessage.class));
    }

    @Test
    void getTicketById_shouldReturnTicketWhenUserIsOwner() {
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

        SupportTicketResponse response = service.getTicketById(100L, user);

        assertNotNull(response);
        assertEquals(100L, response.getId());
    }

    @Test
    void getTicketById_shouldReturnTicketWhenUserIsAdmin() {
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

        SupportTicketResponse response = service.getTicketById(100L, admin);

        assertNotNull(response);
    }

    @Test
    void getTicketById_shouldThrowUnauthorizedWhenUserIsNotOwnerOrAdmin() {
        Role otherRole = new Role(1L, "ROLE_USER");
        User otherUser = new User(3L, "Bob", "bob@example.com", "pass", true, otherRole, null, null);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

        assertThrows(UnauthorizedAccessException.class, () -> service.getTicketById(100L, otherUser));
    }

    @Test
    void sendMessage_shouldSaveMessageAndTouchTicket() {
        CreateSupportMessageRequest request = new CreateSupportMessageRequest("Re-trying upload");
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

        SupportTicketMessage message = SupportTicketMessage.builder()
                .id(200L)
                .ticket(ticket)
                .sender(user)
                .message("Re-trying upload")
                .build();
        when(messageRepository.save(any(SupportTicketMessage.class))).thenReturn(message);

        SupportTicketMessageResponse response = service.sendMessage(100L, request, user);

        assertNotNull(response);
        assertEquals("Re-trying upload", response.getMessage());
        verify(messageRepository, times(1)).save(any(SupportTicketMessage.class));
    }

    @Test
    void updateTicketStatus_shouldUpdateAndLogMessageWhenAdmin() {
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(SupportTicket.class))).thenReturn(ticket);

        SupportTicketResponse response = service.updateTicketStatus(100L, TicketStatus.RESOLVED, admin);

        assertNotNull(response);
        verify(ticketRepository, times(1)).save(any(SupportTicket.class));
        verify(messageRepository, times(1)).save(any(SupportTicketMessage.class));
    }

    @Test
    void updateTicketStatus_shouldThrowUnauthorizedWhenNotAdmin() {
        assertThrows(UnauthorizedAccessException.class, () -> service.updateTicketStatus(100L, TicketStatus.RESOLVED, user));
    }
}
