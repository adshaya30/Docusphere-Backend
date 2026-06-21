package com.docusphere.backend.chat.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docusphere.backend.Common.exception.UnauthorizedAccessException;
import com.docusphere.backend.chat.dto.TeamChatMessageRequest;
import com.docusphere.backend.chat.dto.TeamChatMessageResponse;
import com.docusphere.backend.chat.dto.TeamChatReceiptBatchEvent;
import com.docusphere.backend.chat.dto.TeamChatReceiptRequest;
import com.docusphere.backend.chat.dto.TeamChatReceiptUpdate;
import com.docusphere.backend.team.service.TeamService;

@Service
public class TeamChatService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;
    private final TeamService teamService;

    public TeamChatService(JdbcTemplate jdbcTemplate, TeamService teamService) {
        this.jdbcTemplate = jdbcTemplate;
        this.teamService = teamService;
        initializeSchema();
    }

    @Transactional(readOnly = true)
    public List<TeamChatMessageResponse> getMessages(UUID teamId, Long userId, Integer limit) {
        ensureUserInTeam(userId, teamId);
        int safeLimit = sanitizeLimit(limit);

        String sql = """
                SELECT m.id, m.team_id, m.sender_id, m.sender_name, m.content, m.message_type,
                       m.document_id, m.mention_user_ids, m.created_at, m.is_edited, m.edited_at,
                       m.seen_by, m.delivered_to
                FROM team_chat_messages m
                WHERE m.team_id = ?
                  AND NOT ((',' || COALESCE(m.hidden_for_user_ids, '') || ',') LIKE '%,' || CAST(? AS VARCHAR) || ',%')
                ORDER BY m.created_at DESC
                LIMIT ?
                """;

        List<TeamChatMessageResponse> rows = jdbcTemplate.query(sql, messageRowMapper(), teamId, userId, safeLimit);
        Collections.reverse(rows);
        return rows;
    }

    @Transactional
    public TeamChatMessageResponse createMessage(UUID teamId, Long userId, TeamChatMessageRequest request) {
        ensureUserInTeam(userId, teamId);

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        TeamChatMessageRequest.MessageType messageType = request.getMessageType() == null
                ? TeamChatMessageRequest.MessageType.TEXT
                : request.getMessageType();

        UUID documentId = request.getDocumentId();
        if (messageType == TeamChatMessageRequest.MessageType.CHANGE_REQUEST && documentId == null) {
            throw new IllegalArgumentException("documentId is required for change requests");
        }
        if (documentId != null && !isDocumentInTeam(documentId, teamId)) {
            throw new IllegalArgumentException("The selected document does not belong to this team");
        }

        String senderName = jdbcTemplate.queryForObject(
                "SELECT full_name FROM users WHERE id = ?",
                String.class,
                userId);

        UUID messageId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        List<Long> mentionUserIds = sanitizeMentions(teamId, request.getMentionUserIds());

        jdbcTemplate.update(
                """
                INSERT INTO team_chat_messages
                (id, team_id, sender_id, sender_name, content, message_type, document_id, mention_user_ids, created_at, is_edited, delivered_to)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                messageId,
                teamId,
                userId,
                senderName,
                content,
                messageType.name(),
                documentId,
                serializeMentions(mentionUserIds),
                Timestamp.valueOf(createdAt),
                false,
                serializeMentions(List.of(userId)));  // Initially delivered to sender only

        TeamChatMessageResponse response = new TeamChatMessageResponse();
        response.setId(messageId);
        response.setTeamId(teamId);
        response.setSenderId(userId);
        response.setSenderName(senderName);
        response.setContent(content);
        response.setMessageType(messageType);
        response.setDocumentId(documentId);
        response.setMentionUserIds(mentionUserIds);
        response.setCreatedAt(createdAt);
        response.setEdited(false);
        response.setEditedAt(null);
        response.setSeenBy(Collections.emptyList());
        response.setDeliveredTo(List.of(userId));  // Initially delivered to sender only
        return response;
    }

    @Transactional
    public TeamChatMessageResponse editMessage(UUID teamId, UUID messageId, Long userId, TeamChatMessageRequest request) {
        ensureUserInTeam(userId, teamId);

        // Verify message exists and user is sender
        String senderSql = "SELECT sender_id FROM team_chat_messages WHERE id = ? AND team_id = ?";
        Long senderId = jdbcTemplate.queryForObject(senderSql, Long.class, messageId, teamId);
        
        if (senderId == null || !senderId.equals(userId)) {
            throw new UnauthorizedAccessException("You can only edit your own messages");
        }

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isBlank()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        LocalDateTime editedAt = LocalDateTime.now();

        jdbcTemplate.update(
                """
                UPDATE team_chat_messages
                SET content = ?, is_edited = TRUE, edited_at = ?
                WHERE id = ? AND team_id = ?
                """,
                content,
                Timestamp.valueOf(editedAt),
                messageId,
                teamId);

        // Fetch and return updated message
        return getMessageById(teamId, messageId);
    }

    @Transactional
    public void deleteMessage(UUID teamId, UUID messageId, Long userId, String scope) {
        ensureUserInTeam(userId, teamId);

        String senderSql = "SELECT sender_id FROM team_chat_messages WHERE id = ? AND team_id = ?";
        Long senderId = jdbcTemplate.queryForObject(senderSql, Long.class, messageId, teamId);
        
        if (senderId == null) {
            throw new IllegalArgumentException("Message not found");
        }

        if ("for-everyone".equals(scope)) {
            if (!senderId.equals(userId)) {
                throw new UnauthorizedAccessException("Only the sender can delete for everyone");
            }
            // Hard delete
            jdbcTemplate.update("DELETE FROM team_chat_messages WHERE id = ? AND team_id = ?", messageId, teamId);
        } else {
            // "for-me" — hide only for this user; do not reuse seen_by (used for read receipts).
            String currentHidden = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(hidden_for_user_ids, '') FROM team_chat_messages WHERE id = ? AND team_id = ?",
                    String.class,
                    messageId,
                    teamId);

            // parseMentions() returns an immutable list; copy to mutable before add.
            List<Long> hiddenFor = new ArrayList<>(parseMentions(currentHidden));
            if (!hiddenFor.contains(userId)) {
                hiddenFor.add(userId);
            }

            jdbcTemplate.update(
                    "UPDATE team_chat_messages SET hidden_for_user_ids = ? WHERE id = ? AND team_id = ?",
                    serializeMentions(hiddenFor),
                    messageId,
                    teamId);
        }
    }

    @Transactional(readOnly = true)
    public TeamChatMessageResponse getMessageInfo(UUID teamId, UUID messageId, Long userId) {
        ensureUserInTeam(userId, teamId);
        return getMessageById(teamId, messageId);
    }

    /**
     * Records a delivery or read receipt for one message and returns the updated lists (for realtime broadcast).
     */
    @Transactional
    public TeamChatReceiptUpdate applyReceipt(UUID teamId, Long userId, UUID messageId, String kind) {
        return doApplyReceipt(teamId, userId, messageId, kind);
    }

    @Transactional
    public TeamChatReceiptBatchEvent applyReceiptsBatch(UUID teamId, Long userId, List<TeamChatReceiptRequest> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            return new TeamChatReceiptBatchEvent();
        }
        int limit = Math.min(rawItems.size(), 80);
        List<TeamChatReceiptUpdate> out = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            TeamChatReceiptRequest req = rawItems.get(i);
            if (req.getMessageId() == null || req.getKind() == null) {
                continue;
            }
            TeamChatReceiptUpdate u = doApplyReceipt(teamId, userId, req.getMessageId(), req.getKind());
            if (u != null) {
                out.add(u);
            }
        }
        TeamChatReceiptBatchEvent ev = new TeamChatReceiptBatchEvent();
        ev.setUpdates(out);
        return ev;
    }

    private TeamChatReceiptUpdate doApplyReceipt(UUID teamId, Long userId, UUID messageId, String kind) {
        ensureUserInTeam(userId, teamId);
        Long senderId = jdbcTemplate.queryForObject(
                "SELECT sender_id FROM team_chat_messages WHERE id = ? AND team_id = ?",
                Long.class,
                messageId,
                teamId);

        String upperKind = kind.trim().toUpperCase();
        if ("SEEN".equals(upperKind)) {
            if (senderId.equals(userId)) {
                return null;
            }
            mergeUserIntoChatColumn(messageId, teamId, "seen_by", userId);
            mergeUserIntoChatColumn(messageId, teamId, "delivered_to", userId);
        } else if ("DELIVERED".equals(upperKind)) {
            mergeUserIntoChatColumn(messageId, teamId, "delivered_to", userId);
        } else {
            throw new IllegalArgumentException("kind must be DELIVERED or SEEN");
        }
        return buildReceiptSnapshot(teamId, messageId);
    }

    private TeamChatReceiptUpdate buildReceiptSnapshot(UUID teamId, UUID messageId) {
        TeamChatMessageResponse full = getMessageById(teamId, messageId);
        TeamChatReceiptUpdate u = new TeamChatReceiptUpdate();
        u.setMessageId(messageId);
        u.setDeliveredTo(full.getDeliveredTo());
        u.setSeenBy(full.getSeenBy());
        return u;
    }

    private void mergeUserIntoChatColumn(UUID messageId, UUID teamId, String column, long userId) {
        if (!"seen_by".equals(column) && !"delivered_to".equals(column)) {
            throw new IllegalArgumentException("Invalid receipt column");
        }
        String current = jdbcTemplate.queryForObject(
                "SELECT COALESCE(" + column + ", '') FROM team_chat_messages WHERE id = ? AND team_id = ?",
                String.class,
                messageId,
                teamId);
        List<Long> ids = new ArrayList<>(parseMentions(current));
        if (!ids.contains(userId)) {
            ids.add(userId);
        }
        jdbcTemplate.update(
                "UPDATE team_chat_messages SET " + column + " = ? WHERE id = ? AND team_id = ?",
                serializeMentions(ids),
                messageId,
                teamId);
    }

    private TeamChatMessageResponse getMessageById(UUID teamId, UUID messageId) {
        String sql = """
                SELECT m.id, m.team_id, m.sender_id, m.sender_name, m.content, m.message_type,
                       m.document_id, m.mention_user_ids, m.created_at, m.is_edited, m.edited_at,
                       m.seen_by, m.delivered_to
                FROM team_chat_messages m
                WHERE m.id = ? AND m.team_id = ?
                """;
        return jdbcTemplate.queryForObject(sql, messageRowMapper(), messageId, teamId);
    }

    private void ensureUserInTeam(Long userId, UUID teamId) {
        if (!teamService.findMembership(userId, teamId)
                .filter(member -> !Boolean.FALSE.equals(member.getActive()))
                .isPresent()) {
            throw new UnauthorizedAccessException("You are not authorized to access this team chat");
        }
    }

    private boolean isDocumentInTeam(UUID documentId, UUID teamId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM documents WHERE id = ? AND team_id = ?",
                Integer.class,
                documentId,
                teamId);
        return count != null && count > 0;
    }

    private int sanitizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private List<Long> sanitizeMentions(UUID teamId, List<Long> mentionUserIds) {
        if (mentionUserIds == null || mentionUserIds.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> requested = mentionUserIds.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (requested.isEmpty()) {
            return Collections.emptyList();
        }

        String placeholders = requested.stream().map(x -> "?").collect(Collectors.joining(","));
        List<Object> params = new ArrayList<>();
        params.add(teamId);
        params.addAll(requested);

        String sql = "SELECT user_id FROM team_members WHERE team_id = ? AND user_id IN (" + placeholders + ")";
        List<Long> validMentions = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getLong("user_id"),
                params.toArray());

        return validMentions.stream().distinct().toList();
    }

    private String serializeMentions(List<Long> mentionUserIds) {
        if (mentionUserIds == null || mentionUserIds.isEmpty()) {
            return "";
        }
        return mentionUserIds.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> parseMentions(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Long::valueOf)
                .toList();
    }

    private RowMapper<TeamChatMessageResponse> messageRowMapper() {
        return (rs, rowNum) -> mapRow(rs);
    }

    private TeamChatMessageResponse mapRow(ResultSet rs) throws SQLException {
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp editedAt = rs.getTimestamp("edited_at");
        String messageType = rs.getString("message_type");

        TeamChatMessageResponse response = new TeamChatMessageResponse();
        response.setId(rs.getObject("id", UUID.class));
        response.setTeamId(rs.getObject("team_id", UUID.class));
        response.setSenderId(rs.getLong("sender_id"));
        response.setSenderName(rs.getString("sender_name"));
        response.setContent(rs.getString("content"));
        response.setMessageType(TeamChatMessageRequest.MessageType.valueOf(messageType));
        response.setDocumentId(rs.getObject("document_id", UUID.class));
        response.setMentionUserIds(parseMentions(rs.getString("mention_user_ids")));
        response.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : LocalDateTime.now());
        response.setEdited(rs.getBoolean("is_edited"));
        response.setEditedAt(editedAt != null ? editedAt.toLocalDateTime() : null);
        response.setSeenBy(parseMentions(rs.getString("seen_by")));
        response.setDeliveredTo(parseMentions(rs.getString("delivered_to")));
        return response;
    }

    private void initializeSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS team_chat_messages (
                    id UUID PRIMARY KEY,
                    team_id UUID NOT NULL REFERENCES team(id) ON DELETE CASCADE,
                    sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    sender_name VARCHAR(255) NOT NULL,
                    content TEXT NOT NULL,
                    message_type VARCHAR(30) NOT NULL,
                    document_id UUID REFERENCES documents(id) ON DELETE SET NULL,
                    mention_user_ids TEXT,
                    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    is_edited BOOLEAN DEFAULT FALSE,
                    edited_at TIMESTAMP WITHOUT TIME ZONE,
                    seen_by TEXT,
                    delivered_to TEXT,
                    hidden_for_user_ids TEXT
                )
                """);

        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_team_chat_messages_team_created
                ON team_chat_messages(team_id, created_at DESC)
                """);

        jdbcTemplate.execute(
            "ALTER TABLE team_chat_messages ADD COLUMN IF NOT EXISTS is_edited BOOLEAN DEFAULT FALSE");
        jdbcTemplate.execute(
            "ALTER TABLE team_chat_messages ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP WITHOUT TIME ZONE");
        jdbcTemplate.execute(
            "ALTER TABLE team_chat_messages ADD COLUMN IF NOT EXISTS seen_by TEXT");
        jdbcTemplate.execute(
            "ALTER TABLE team_chat_messages ADD COLUMN IF NOT EXISTS delivered_to TEXT");
        jdbcTemplate.execute(
                "ALTER TABLE team_chat_messages ADD COLUMN IF NOT EXISTS hidden_for_user_ids TEXT");
    }
}
