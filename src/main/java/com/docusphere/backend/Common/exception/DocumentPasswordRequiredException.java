package com.docusphere.backend.Common.exception;

public class DocumentPasswordRequiredException extends RuntimeException {

    public DocumentPasswordRequiredException(String message) {
        super(message);
    }
}
