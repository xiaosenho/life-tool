package com.lifetool.auth;

import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTtlMs;
    private final long refreshTtlMs;
    private final Set<String> revokedTokens = ConcurrentHashMap.newKeySet();

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-ttl-ms:900000}") long accessTtlMs,
            @Value("${jwt.refresh-ttl-ms:604800000}") long refreshTtlMs) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.accessTtlMs = accessTtlMs;
        this.refreshTtlMs = refreshTtlMs;
    }

    public String generateAccessToken(String userId) {
        return buildToken(userId, accessTtlMs, "access");
    }

    public String generateRefreshToken(String userId) {
        return buildToken(userId, refreshTtlMs, "refresh");
    }

    public String getUserId(String token) {
        return parseClaims(token).getSubject();
    }

    public String getTokenType(String token) {
        return parseClaims(token).get("type", String.class);
    }

    public boolean validate(String token) {
        try {
            Claims claims = parseClaims(token);
            return !revokedTokens.contains(claims.getId());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public void revoke(String token) {
        try {
            revokedTokens.add(parseClaims(token).getId());
        } catch (JwtException | IllegalArgumentException ignored) {
        }
    }

    private String buildToken(String userId, long ttlMs, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(ttlMs)))
                .signWith(key)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
