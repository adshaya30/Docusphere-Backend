package com.docusphere.backend.documentAction.service;

import java.util.UUID;

public interface TeamAccessValidator {
    boolean isMember(Long userId, UUID teamId);
}
