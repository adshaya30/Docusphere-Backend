package com.docusphere.backend.chat.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.docusphere.backend.authentication.service.JwtService;
import com.docusphere.backend.chat.dto.TeamChatMessageRequest;
import com.docusphere.backend.chat.dto.TeamChatMessageResponse;
import com.docusphere.backend.chat.dto.DeleteMessageEvent;
import com.docusphere.backend.chat.dto.TeamChatReceiptBatchEvent;
import com.docusphere.backend.chat.dto.TeamChatReceiptBatchRequest;
import com.docusphere.backend.chat.service.TeamChatService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/teams/{teamId}/chat")
public class TeamChatController {

    private final TeamChatService teamChatService;
    private final JwtService jwtService;
    private final SimpMessagingTemplate messagingTemplate;

    public TeamChatController(TeamChatService teamChatService, JwtService jwtService, SimpMessagingTemplate messagingTemplate) {
        this.teamChatService = teamChatService;
        this.jwtService = jwtService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/messages")
    public ResponseEntity<List<TeamChatMessageResponse>> getMessages(
            @PathVariable UUID teamId,
            @RequestParam(required = false) Integer limit,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        try {
            if (teamId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "teamId is required");
            }
            Long userId = getUserId(request, token);
            List<TeamChatMessageResponse> messages = teamChatService.getMessages(teamId, userId, limit);
            return ResponseEntity.ok(messages);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (com.docusphere.backend.Common.exception.UnauthorizedAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (Exception ex) {
            // Log and return internal server error without exposing stack traces
            ex.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load chat messages");
        }
    }

    @PostMapping("/messages")
    public ResponseEntity<TeamChatMessageResponse> sendMessage(
            @PathVariable UUID teamId,
            @Valid @RequestBody TeamChatMessageRequest body,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        try {
            if (teamId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "teamId is required");
            }
            Long userId = getUserId(request, token);
            TeamChatMessageResponse created = teamChatService.createMessage(teamId, userId, body);
            messagingTemplate.convertAndSend("/topic/teams/" + teamId + "/chat", created);
            return ResponseEntity.ok(created);
        } catch (com.docusphere.backend.Common.exception.UnauthorizedAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send chat message");
        }
    }

    @PutMapping("/messages/{messageId}")
    public ResponseEntity<TeamChatMessageResponse> editMessage(
            @PathVariable UUID teamId,
            @PathVariable UUID messageId,
            @Valid @RequestBody TeamChatMessageRequest body,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        try {
            Long userId = getUserId(request, token);
            TeamChatMessageResponse edited = teamChatService.editMessage(teamId, messageId, userId, body);
            messagingTemplate.convertAndSend("/topic/teams/" + teamId + "/chat", edited);
            return ResponseEntity.ok(edited);
        } catch (com.docusphere.backend.Common.exception.UnauthorizedAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to edit chat message");
        }
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable UUID teamId,
            @PathVariable UUID messageId,
            @RequestParam(defaultValue = "for-me") String scope,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        try {
            Long userId = getUserId(request, token);
            teamChatService.deleteMessage(teamId, messageId, userId, scope);
            // Broadcast deletion to all users
            messagingTemplate.convertAndSend("/topic/teams/" + teamId + "/chat", 
                new DeleteMessageEvent(messageId, userId, scope));
            return ResponseEntity.noContent().build();
        } catch (com.docusphere.backend.Common.exception.UnauthorizedAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to delete chat message");
        }
    }

    @GetMapping("/messages/{messageId}/info")
    public ResponseEntity<TeamChatMessageResponse> getMessageInfo(
            @PathVariable UUID teamId,
            @PathVariable UUID messageId,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        try {
            Long userId = getUserId(request, token);
            return ResponseEntity.ok(teamChatService.getMessageInfo(teamId, messageId, userId));
        } catch (com.docusphere.backend.Common.exception.UnauthorizedAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load message info");
        }
    }

    /** Batch delivery/read receipts (REST fallback when socket is unavailable). */
    @PostMapping("/receipts")
    public ResponseEntity<Void> postReceipts(
            @PathVariable UUID teamId,
            @Valid @RequestBody TeamChatReceiptBatchRequest body,
            @RequestHeader(value = "Authorization", required = false) String token,
            HttpServletRequest request) {
        try {
            Long userId = getUserId(request, token);
            TeamChatReceiptBatchEvent ev = teamChatService.applyReceiptsBatch(teamId, userId, body.getItems());
            if (ev.getUpdates() != null && !ev.getUpdates().isEmpty()) {
                messagingTemplate.convertAndSend("/topic/teams/" + teamId + "/chat", ev);
            }
            return ResponseEntity.accepted().build();
        } catch (com.docusphere.backend.Common.exception.UnauthorizedAccessException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to apply receipts");
        }
    }

    private Long getUserId(HttpServletRequest request, String headerToken) {
        String token = null;
        if (headerToken != null && headerToken.startsWith("Bearer ")) {
            token = headerToken.substring(7);
        } else if (request != null && request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie c : request.getCookies()) {
                if ("accessToken".equalsIgnoreCase(c.getName()) || "jwt".equalsIgnoreCase(c.getName()) || "Authorization".equalsIgnoreCase(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }

        if (token != null && !token.isBlank()) {
            return jwtService.extractUserId(token);
        }

        throw new IllegalArgumentException("Invalid or missing Authorization header or accessToken cookie");
    }
}
