package com.docusphere.backend.Common.exception;

import org.springframework.security.core.AuthenticationException;
import java.time.LocalDateTime;

public class AccountLockedException extends AuthenticationException {
    private LocalDateTime lockedUntil;

    public AccountLockedException(String msg) {
        super(msg);
    }

    public AccountLockedException(String msg, LocalDateTime lockedUntil) {
        super(msg);
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
}
