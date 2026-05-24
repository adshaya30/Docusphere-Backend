package com.docusphere.backend.authentication.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class JwtServiceTest {

    private static final long ONE_DAY = 86400000L;
    private static final long SEVEN_DAYS = 604800000L;
    private static final long TOLERANCE_MS = 5000L;

    private final JwtService jwtService;

    @Autowired
    JwtServiceTest(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Test
    @DisplayName("generateRefreshToken - rememberMe=false uses 1 day expiry and false claim")
    void generateRefreshToken_whenRememberMeFalse_shouldUseOneDayExpiry() {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("john@example.com")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        long start = System.currentTimeMillis();
        String token = jwtService.generateRefreshToken(userDetails, 1L, false);
        long expiration = jwtService.extractExpiration(token).getTime();

        assertFalse(jwtService.extractRememberMe(token));
        assertTrue(expiration >= start + ONE_DAY - TOLERANCE_MS);
        assertTrue(expiration <= start + ONE_DAY + TOLERANCE_MS);
    }

    @Test
    @DisplayName("generateRefreshToken - rememberMe=true uses 7 day expiry and true claim")
    void generateRefreshToken_whenRememberMeTrue_shouldUseSevenDayExpiry() {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("john@example.com")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        long start = System.currentTimeMillis();
        String token = jwtService.generateRefreshToken(userDetails, 1L, true);
        long expiration = jwtService.extractExpiration(token).getTime();

        assertTrue(jwtService.extractRememberMe(token));
        assertTrue(expiration >= start + SEVEN_DAYS - TOLERANCE_MS);
        assertTrue(expiration <= start + SEVEN_DAYS + TOLERANCE_MS);
    }

    @Test
    @DisplayName("generateRefreshToken - rememberMe=true expires later than false by about 6 days")
    void generateRefreshToken_whenCompareRememberMeModes_shouldDifferBySixDays() {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("john@example.com")
                .password("password")
                .authorities("ROLE_USER")
                .build();

        String shortToken = jwtService.generateRefreshToken(userDetails, 1L, false);
        String longToken = jwtService.generateRefreshToken(userDetails, 1L, true);

        long shortExpiration = jwtService.extractExpiration(shortToken).getTime();
        long longExpiration = jwtService.extractExpiration(longToken).getTime();
        long gap = longExpiration - shortExpiration;
        long expectedGap = SEVEN_DAYS - ONE_DAY;

        assertTrue(gap >= expectedGap - TOLERANCE_MS);
        assertTrue(gap <= expectedGap + TOLERANCE_MS);
    }
}



