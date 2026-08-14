package com.docusphere.backend.onlyoffice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Short-lived signed token embedded in ONLYOFFICE document URLs so the document server
 * can fetch file bytes without bypassing password verification or share authorization.
 */
@Service
public class OnlyOfficeDownloadTokenService {

    private static final long TTL_MS = 60 * 60 * 1000L;

    @Value("${app.onlyoffice.jwt.secret:V8pX9iu5gDWzQrHP5Od62XOOiuOnlrtF}")
    private String secretKey;

    public String createDocumentDownloadToken(UUID documentId, Long userId, String shareToken) {
        var builder = Jwts.builder()
                .claim("documentId", documentId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TTL_MS));

        if (userId != null) {
            builder.claim("userId", userId);
        }
        if (shareToken != null && !shareToken.isBlank()) {
            builder.claim("shareToken", shareToken.trim());
        }

        return builder.signWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8))).compact();
    }

    public String createVersionDownloadToken(UUID documentId, UUID versionId, Long userId, String shareToken) {
        var builder = Jwts.builder()
                .claim("documentId", documentId.toString())
                .claim("versionId", versionId.toString())
                .claim("userId", userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + TTL_MS));

        if (shareToken != null && !shareToken.isBlank()) {
            builder.claim("shareToken", shareToken.trim());
        }

        return builder.signWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8))).compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
