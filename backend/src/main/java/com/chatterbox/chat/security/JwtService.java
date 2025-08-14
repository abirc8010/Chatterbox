package com.chatterbox.chat.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;

    public JwtService(@Value("${CHATTERBOX_JWT_SECRET:change-this-development-secret-to-a-long-value}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String issue(String email) {
        Date now = new Date();
        return Jwts.builder().claims(Map.of("email", email)).subject(email).issuedAt(now)
                .expiration(new Date(now.getTime() + 86_400_000L)).signWith(key).compact();
    }

    public io.jsonwebtoken.Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
