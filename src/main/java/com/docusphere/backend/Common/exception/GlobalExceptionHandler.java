package com.docusphere.backend.Common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
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

    // Handle Duplicate Email
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Object> handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", ex.getMessage());
    }

    // Handle Password Mismatch
    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<Object> handleInvalidPassword(InvalidPasswordException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", ex.getMessage());
    }

    // Handle User Not Found
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Object> handleUserNotFound(UserNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", ex.getMessage());
    }

    // Handle Invalid Token
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Object> handleInvalidToken(InvalidTokenException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", ex.getMessage());
    }

    // Handle Token Expired
    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<Object> handleTokenExpired(TokenExpiredException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "TOKEN_EXPIRED", ex.getMessage());
    }

    // Handle Email Not Verified
    @ExceptionHandler(EmailNotVerifiedException.class)
    public ResponseEntity<Object> handleEmailNotVerified(EmailNotVerifiedException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "EMAIL_NOT_VERIFIED", ex.getMessage());
    }

    // Handle Authentication Failures (wrong password, user not found, etc.)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Invalid email or password. Please try again.");
    }

    // Handle other authentication exceptions
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthenticationException(AuthenticationException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED",
                "Authentication failed. Please check your credentials.");
    }
    // Handle Document Not Found
    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<Object> handleDocumentNotFound(DocumentNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", ex.getMessage());
    }

    // Handle File Upload Error
    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<Object> handleFileUploadException(FileUploadException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "FILE_UPLOAD_ERROR", ex.getMessage());
    }

    // Handle Invalid Request
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Object> handleInvalidRequest(InvalidRequestException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", ex.getMessage());
    }

    // Handle @Valid payload errors (RenameRequest, MoveRequest validation failures)

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    // Handle invalid method arguments ( malformed UUID conversions, illegal enum/value parsing)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", ex.getMessage());
    }


    // Handle Unauthorized Access
    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<Object> handleUnauthorizedAccess(UnauthorizedAccessException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "UNAUTHORIZED_ACCESS", ex.getMessage());
    }


    // Handle any other unexpected exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGlobalException(Exception ex) {
        log.error("Unexpected error occurred:", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred. Please try again later.");
    }
    // Handle No Resource Found
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNoResourceFound(NoResourceFoundException ex) {
        return buildErrorResponse(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "Requested resource was not found."
        );
    }

    // Helper method to build consistent error response
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
