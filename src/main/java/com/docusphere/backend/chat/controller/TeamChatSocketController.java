package com.docusphere.backend.chat.controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.docusphere.backend.chat.dto.TeamChatMessageRequest;
import com.docusphere.backend.chat.dto.TeamChatMessageResponse;
import com.docusphere.backend.chat.dto.TeamChatReceiptBatchEvent;
import com.docusphere.backend.chat.dto.TeamChatReceiptRequest;
import com.docusphere.backend.chat.dto.TeamChatReceiptUpdate;
import com.docusphere.backend.chat.service.TeamChatService;

import jakarta.validation.Valid;

@Controller
public class TeamChatSocketController {

    private final TeamChatService teamChatService;
    private final SimpMessagingTemplate messagingTemplate;

    public TeamChatSocketController(TeamChatService teamChatService, SimpMessagingTemplate messagingTemplate) {
        this.teamChatService = teamChatService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/teams/{teamId}/chat.send")
    public void sendMessage(
            @DestinationVariable UUID teamId,
            @Valid TeamChatMessageRequest request,
            Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        TeamChatMessageResponse created = teamChatService.createMessage(teamId, userId, request);
        messagingTemplate.convertAndSend("/topic/teams/" + teamId + "/chat", created);
    }

    @MessageMapping("/teams/{teamId}/chat.receipt")
    public void receipt(
            @DestinationVariable UUID teamId,
            @Valid @Payload TeamChatReceiptRequest request,
            Principal principal) {
        Long userId = Long.parseLong(principal.getName());
        TeamChatReceiptUpdate update = teamChatService.applyReceipt(teamId, userId, request.getMessageId(), request.getKind());
        if (update == null) {
            return;
        }
        TeamChatReceiptBatchEvent batch = new TeamChatReceiptBatchEvent();
        batch.setUpdates(List.of(update));
        messagingTemplate.convertAndSend("/topic/teams/" + teamId + "/chat", batch);
    }
}
