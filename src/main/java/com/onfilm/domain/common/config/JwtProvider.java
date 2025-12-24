package com.onfilm.domain.common.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Component
public class JwtProvider {

    // 예: application.yml에서 secret 주입
    private final Key key;

    public JwtProvider(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(Long userId, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public String createRefreshToken() {
        byte[] random = new byte[64];
        SecureRandomHolder.INSTANCE.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    public boolean validate(String token) {
        try {
            Jwts.parser().verifyWith((SecretKey) key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long parseUserId(String token) {
        Claims claims = Jwts.parser().verifyWith((SecretKey) key).build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.valueOf(claims.getSubject());
    }

    public Authentication getAuthentication(String token) {
        Long userId = parseUserId(token);

        // 여기서는 userId만 principal로 둠 (원하면 UserDetails 조회해서 권한 세팅 가능)
        User principal = new User(String.valueOf(userId), "", List.of());
        return new UsernamePasswordAuthenticationToken(principal, token, principal.getAuthorities());
    }

    private static final class SecureRandomHolder {
        private static final java.security.SecureRandom INSTANCE = new java.security.SecureRandom();
    }
}
