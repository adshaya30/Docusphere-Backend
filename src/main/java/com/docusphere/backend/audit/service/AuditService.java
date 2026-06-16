package com.docusphere.backend.audit.service;

import java.util.Map;

public interface AuditService {
    /**
     * Record an audit event with a simple action key and additional metadata.
     */
    void record(String action, Map<String, Object> metadata);
}

