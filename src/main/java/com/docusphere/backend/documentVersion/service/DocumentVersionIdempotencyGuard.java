package com.docusphere.backend.documentVersion.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents duplicate version rows and repeated ONLYOFFICE save processing from rapid callbacks
 * (e.g. status 6 force-save followed by status 2 close-save for the same editor session).
 */
@Component
public class DocumentVersionIdempotencyGuard {

    static final long DEDUP_WINDOW_MS = 90_000;

    private final Map<String, Long> processedKeys = new ConcurrentHashMap<>();

    public boolean shouldSkipDuplicateSave(UUID documentId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return false;
        }
        cleanupExpiredEntries();
        String key = documentId + ":save:" + idempotencyKey.trim();
        Long lastSeenAt = processedKeys.get(key);
        long now = System.currentTimeMillis();
        return lastSeenAt != null && (now - lastSeenAt) <= DEDUP_WINDOW_MS;
    }

    public void recordSave(UUID documentId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        cleanupExpiredEntries();
        processedKeys.put(documentId + ":save:" + idempotencyKey.trim(), System.currentTimeMillis());
    }

    public boolean shouldSkipVersionCreation(
            UUID documentId,
            Long userId,
            String idempotencyKey,
            LocalDateTime documentUpdatedAt
    ) {
        cleanupExpiredEntries();

        String key = buildKey(documentId, userId, idempotencyKey, documentUpdatedAt);
        long now = System.currentTimeMillis();
        Long lastSeenAt = processedKeys.get(key);

        return lastSeenAt != null && (now - lastSeenAt) <= DEDUP_WINDOW_MS;
    }

    public void recordVersionCreation(
            UUID documentId,
            Long userId,
            String idempotencyKey,
            LocalDateTime documentUpdatedAt
    ) {
        cleanupExpiredEntries();
        String key = buildKey(documentId, userId, idempotencyKey, documentUpdatedAt);
        processedKeys.put(key, System.currentTimeMillis());
    }

    private void cleanupExpiredEntries() {
        long now = System.currentTimeMillis();
        processedKeys.entrySet().removeIf(entry -> (now - entry.getValue()) > DEDUP_WINDOW_MS);
    }

    private String buildKey(UUID documentId, Long userId, String idempotencyKey, LocalDateTime documentUpdatedAt) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return documentId + ":idem:" + idempotencyKey.trim();
        }
        if (documentUpdatedAt != null) {
            return documentId + ":mod:" + documentUpdatedAt;
        }
        return documentId + ":user:" + userId;
    }
}