package com.docusphere.backend.documentVersion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("DocumentVersionSummaryStore Unit Tests")
class DocumentVersionSummaryStoreTest {

    private DocumentVersionSummaryStore summaryStore;
    private UUID documentId;
    private Long userId;

    @BeforeEach
    void setUp() {
        summaryStore = new DocumentVersionSummaryStore();
        documentId = UUID.randomUUID();
        userId = 42L;
    }

    @Test
    @DisplayName("Should store and peek pending change summary")
    void storeAndPeek_success() {
        summaryStore.store(documentId, userId, "Updated introduction");

        assertEquals("Updated introduction", summaryStore.peek(documentId, userId));
    }

    @Test
    @DisplayName("Should consume and remove pending change summary")
    void consume_removesSummary() {
        summaryStore.store(documentId, userId, "Fixed typos");

        assertEquals("Fixed typos", summaryStore.consume(documentId, userId));
        assertNull(summaryStore.peek(documentId, userId));
        assertNull(summaryStore.consume(documentId, userId));
    }

    @Test
    @DisplayName("Should isolate summaries per document")
    void store_isolatedByDocument() {
        UUID otherDocumentId = UUID.randomUUID();

        summaryStore.store(documentId, userId, "Owner summary");
        summaryStore.store(otherDocumentId, userId, "Other document summary");

        assertEquals("Owner summary", summaryStore.peek(documentId, userId));
        assertEquals("Other document summary", summaryStore.peek(otherDocumentId, userId));
    }

    @Test
    @DisplayName("Should resolve summary by document when user key differs")
    void peek_fallsBackToDocumentKey() {
        summaryStore.store(documentId, 99L, "Shared edit summary");

        assertEquals("Shared edit summary", summaryStore.peek(documentId, 42L));
    }
}
