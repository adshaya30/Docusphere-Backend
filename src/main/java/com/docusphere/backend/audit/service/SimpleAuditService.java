package com.docusphere.backend.audit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SimpleAuditService implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(SimpleAuditService.class);

    @Override
    public void record(String action, Map<String, Object> metadata) {
        // Log as info for now. In real systems this would persist to audit store.
        log.info("AUDIT action={} metadata={}", action, metadata);
    }
}

