package com.docusphere.backend.documentVersion.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents duplicate version rows from rapid ONLYOFFICE callbacks, force-save + autosave,
 * or repeated frontend save requests within a short window.
 */
@Component
public class DocumentVersionIdempotencyGuard {

    static final long DEDUP_WINDOW_MS = 5000;

    private final Map<String, Long> processedKeys = new ConcurrentHashMap<>();

    // Disabled in safe mode — always create a version on every valid edit
    public boolean shouldSkipVersionCreation(
            UUID documentId,
            Long userId,
            String idempotencyKey,
            LocalDateTime documentUpdatedAt
    ) {
        return false;
    }

    public void recordVersionCreation(
            UUID documentId,
            Long userId,
            String idempotencyKey,
            LocalDateTime documentUpdatedAt
    ) {
        String key = buildKey(documentId, userId, idempotencyKey, documentUpdatedAt);
        processedKeys.put(key, System.currentTimeMillis());
    }

    private String buildKey(UUID documentId, Long userId, String idempotencyKey, LocalDateTime documentUpdatedAt) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return documentId + ":idem:" + idempotencyKey.trim();
        }
        if (documentUpdatedAt != null) {
            return documentId + ":user:" + userId + ":mod:" + documentUpdatedAt;
        }
        return documentId + ":user:" + userId;
    }
}
