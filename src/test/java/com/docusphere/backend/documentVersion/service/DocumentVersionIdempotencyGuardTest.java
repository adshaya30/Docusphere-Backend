package com.docusphere.backend.documentVersion.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DocumentVersionIdempotencyGuard Unit Tests")
class DocumentVersionIdempotencyGuardTest {

    private final DocumentVersionIdempotencyGuard guard = new DocumentVersionIdempotencyGuard();

    @Test
    @DisplayName("Should skip duplicate ONLYOFFICE save for same editor session key")
    void shouldSkipDuplicateSave_sameKey() {
        UUID documentId = UUID.randomUUID();
        String sessionKey = "ds_abc123_v2";

        assertFalse(guard.shouldSkipDuplicateSave(documentId, sessionKey));

        guard.recordSave(documentId, sessionKey);

        assertTrue(guard.shouldSkipDuplicateSave(documentId, sessionKey));
        assertFalse(guard.shouldSkipDuplicateSave(documentId, "different-key"));
    }
}
