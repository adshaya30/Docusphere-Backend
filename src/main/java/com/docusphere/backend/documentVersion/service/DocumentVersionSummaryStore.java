package com.docusphere.backend.documentVersion.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DocumentVersionSummaryStore {

    private static final long TTL_SECONDS = 900;

    private final Map<String, PendingSummary> pendingSummaries = new ConcurrentHashMap<>();

    public void store(UUID documentId, Long userId, String changeSummary) {
        pendingSummaries.put(cacheKey(documentId, userId), new PendingSummary(changeSummary, Instant.now().plusSeconds(TTL_SECONDS)));
    }

    public String peek(UUID documentId, Long userId) {
        PendingSummary pending = pendingSummaries.get(cacheKey(documentId, userId));
        if (pending == null) {
            return null;
        }
        if (Instant.now().isAfter(pending.expiresAt())) {
            pendingSummaries.remove(cacheKey(documentId, userId));
            return null;
        }
        return pending.changeSummary();
    }

    public String consume(UUID documentId, Long userId) {
        PendingSummary pending = pendingSummaries.remove(cacheKey(documentId, userId));
        if (pending == null) {
            return null;
        }
        if (Instant.now().isAfter(pending.expiresAt())) {
            return null;
        }
        return pending.changeSummary();
    }

    private String cacheKey(UUID documentId, Long userId) {
        return documentId + ":" + userId;
    }

    private record PendingSummary(String changeSummary, Instant expiresAt) {
    }
}
