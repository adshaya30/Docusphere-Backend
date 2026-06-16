package com.docusphere.backend.documentProtection.service;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DocumentPasswordVerificationStore {

    private static final long TTL_SECONDS = 900;

    private final Map<String, Instant> verifiedUntil = new ConcurrentHashMap<>();

    public void markVerified(UUID documentId, String principalKey) {
        verifiedUntil.put(cacheKey(documentId, principalKey), Instant.now().plusSeconds(TTL_SECONDS));
    }

    public boolean isVerified(UUID documentId, String principalKey) {
        Instant expiry = verifiedUntil.get(cacheKey(documentId, principalKey));
        if (expiry == null) {
            return false;
        }
        if (Instant.now().isAfter(expiry)) {
            verifiedUntil.remove(cacheKey(documentId, principalKey));
            return false;
        }
        return true;
    }

    public void clearVerification(UUID documentId, String principalKey) {
        verifiedUntil.remove(cacheKey(documentId, principalKey));
    }

    public void clearAllForDocument(UUID documentId) {
        String prefix = documentId + ":";
        verifiedUntil.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private String cacheKey(UUID documentId, String principalKey) {
        return documentId + ":" + principalKey;
    }
}
