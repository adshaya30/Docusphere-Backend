package com.docusphere.backend.Common.util;

import com.docusphere.backend.Common.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern LOWERCASE = Pattern.compile(".*[a-z].*");
    private static final Pattern DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern SPECIAL = Pattern.compile(".*[@$!%*?&].*");

    /**
     * Validate password according to policy and throw InvalidRequestException with
     * the first failing rule message.
     */
    public void validateOrThrow(String password) {
        if (password == null || password.isBlank()) {
            throw new InvalidRequestException("password is required");
        }

        if (password.length() < MIN_LENGTH) {
            throw new InvalidRequestException("Password must be at least 8 characters long.");
        }

        if (!UPPERCASE.matcher(password).matches()) {
            throw new InvalidRequestException("Password must contain at least one uppercase letter.");
        }

        if (!LOWERCASE.matcher(password).matches()) {
            throw new InvalidRequestException("Password must contain at least one lowercase letter.");
        }

        if (!DIGIT.matcher(password).matches()) {
            throw new InvalidRequestException("Password must contain at least one number.");
        }

        if (!SPECIAL.matcher(password).matches()) {
            throw new InvalidRequestException("Password must contain at least one special character.");
        }
    }
}

