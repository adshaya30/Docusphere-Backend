package com.docusphere.backend.authentication.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {
    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access.expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh.expiration}")
    private long refreshTokenExpiration;

    @Value("${app.session.expiry}")
    private long sessionRefreshTokenExpiration;

    @Value("${app.session.remember-me-expiry}")
    private long rememberMeRefreshTokenExpiration;


    //Creates the signing key from the secret
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public String generateAccessToken(UserDetails userDetails, String role) {
        return generateAccessToken(userDetails, role, null);
    }

    public String generateAccessToken(UserDetails userDetails, String role, Long userId) {
        return buildToken(userDetails, role, userId, accessTokenExpiration);
    }

    // Generate Refresh Token (long lived)
    public String generateRefreshToken(UserDetails userDetails) {
        return generateRefreshToken(userDetails, null, true);
    }

    public String generateRefreshToken(UserDetails userDetails, Long userId) {
        return generateRefreshToken(userDetails, userId, true);
    }

    public String generateRefreshToken(UserDetails userDetails, Long userId, boolean rememberMe) {
        long refreshExpiry = rememberMe ? rememberMeRefreshTokenExpiration : sessionRefreshTokenExpiration;
        return buildToken(userDetails, "REFRESH", userId, refreshExpiry, rememberMe);
    }

    private String buildToken(UserDetails userDetails, String role, Long userId, long expiry) {
        return buildToken(userDetails, role, userId, expiry, null);
    }

    private String buildToken(UserDetails userDetails, String role, Long userId, long expiry, Boolean rememberMe) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        if (userId != null) {
            claims.put("userId", userId);
        }
        if (rememberMe != null) {
            claims.put("rememberMe", rememberMe);
        }

        return Jwts.builder()
        .claims(claims)
        .subject(userDetails.getUsername())
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expiry))
        .signWith(getSigningKey())
        .compact();
    }

    // Backward-compatible methods used by existing controller/tests
    public String generateToken(UserDetails userDetails, String role, Long userId) {
        return buildToken(userDetails, role, userId, accessTokenExpiration);
    }

    public String generateToken(UserDetails userDetails, String role, Long userId, boolean rememberMe) {
        long expiry = rememberMe ? refreshTokenExpiration : accessTokenExpiration;
        return buildToken(userDetails, role, userId, expiry);
    }
    public String extractUsername(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
    public String extractRole(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }
    public Long extractUserId(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("userId", Long.class);
    }

    public boolean extractRememberMe(String token) {
        Boolean rememberMe = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("rememberMe", Boolean.class);
        return rememberMe == null || rememberMe;
    }

    public Date extractExpiration(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
    //Check if token is expired
    private boolean isTokenExpired(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .before(new Date());
    }
}
