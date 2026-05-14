package com.back.together02be.global.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ClaimsBuilder;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;

import io.jsonwebtoken.security.Keys;

public class JwtUtil {

    public static String generateAccessToken(
            String secret,
            long expireSeconds,
            Map<String, Object> body
    ) {
        ClaimsBuilder claimsBuilder = Jwts.claims();

        for (Map.Entry<String, Object> entry : body.entrySet()) {
            claimsBuilder.add(entry.getKey(), entry.getValue());
        }

        Claims claims = claimsBuilder.build();

        Date issuedAt = new Date();
        Date expiration = new Date(issuedAt.getTime() + 1000L * expireSeconds);

        Key secretKey = Keys.hmacShaKeyFor(secret.getBytes());

        return Jwts.builder()
                .claims(claims)
                .issuedAt(issuedAt)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public static boolean isValid(String token, String secret) {

        SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parse(token)
                    .getPayload();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Map<String, Object> payloadOrNull(String token, String secret) {

        SecretKey secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        if (isValid(token, secret)) {
            return (Map<String, Object>) Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parse(token)
                    .getPayload();
        }

        return null;
    }
}
