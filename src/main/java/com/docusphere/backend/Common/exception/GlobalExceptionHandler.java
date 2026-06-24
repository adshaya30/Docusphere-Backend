package com.docusphere.backend.Common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // AUTH & USER 

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Object> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<Object> handleInvalidPassword(InvalidPasswordException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", ex.getMessage());
    }

    @ExceptionHandler(DocumentPasswordRequiredException.class)
    public ResponseEntity<Object> handleDocumentPasswordRequired(DocumentPasswordRequiredException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "DOCUMENT_PASSWORD_REQUIRED", ex.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Object> handleUserNotFound(UserNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<Object> handleEmailNotVerified(EmailNotVerifiedException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "EMAIL_NOT_VERIFIED", ex.getMessage());
    }

    //TOKEN

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Object> handleInvalidToken(InvalidTokenException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", ex.getMessage());
    }

    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<Object> handleTokenExpired(TokenExpiredException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED", ex.getMessage());
    }

    // AUTHENTICATION 

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("Email not verified")) {
            return buildErrorResponse(HttpStatus.UNAUTHORIZED, "EMAIL_NOT_VERIFIED", ex.getMessage());
        }

        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Invalid email or password. Please try again.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                "Authentication failed. Please check your credentials.");
    }

    // Handle @Valid body validation failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed for request body.");

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    // Handle missing query params like ?token= and ?email=
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Object> handleMissingRequestParam(MissingServletRequestParameterException ex) {
        String message = ex.getParameterName() + " parameter is required";
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "MISSING_REQUEST_PARAMETER", message);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Object> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return buildErrorResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage());
    }

    // Handle any other unexpected exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGlobalException(Exception ex) {
        log.error("Unexpected error occurred:", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again later.");
    }

    // (validation handled by handleMethodArgumentNotValid)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
    }

    // ACCESS 

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<Object> handleUnauthorizedAccess(UnauthorizedAccessException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "UNAUTHORIZED_ACCESS", ex.getMessage());
    }

    // ADMIN
    @ExceptionHandler(AdminDashboardException.class)
    public ResponseEntity<Object> handleAdminDashboardException(AdminDashboardException ex) {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "ADMIN_DASHBOARD_ERROR", ex.getMessage());
    }

    // RESOURCE 
    // RESOURCE 
    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<Object> handleEntityNotFound(jakarta.persistence.EntityNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "ENTITY_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNoResourceFound(NoResourceFoundException ex) {
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "Requested resource was not found."
        );
    }

    //GLOBAL


    //  COMMON RESPONSE 

    private ResponseEntity<Object> buildErrorResponse(HttpStatus status, String errorCode, String message) {

        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", status.value());
        errorResponse.put("error", status.getReasonPhrase());
        errorResponse.put("errorCode", errorCode);
        errorResponse.put("message", message);

        return new ResponseEntity<>(errorResponse, status);
    }
}
