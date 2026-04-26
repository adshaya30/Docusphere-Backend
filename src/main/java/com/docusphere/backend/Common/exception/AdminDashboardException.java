package com.docusphere.backend.Common.exception;

public class AdminDashboardException extends RuntimeException {
    public AdminDashboardException(String message) {
        super(message);
    }

    public AdminDashboardException(String message, Throwable cause) {
        super(message, cause);
    }
}
