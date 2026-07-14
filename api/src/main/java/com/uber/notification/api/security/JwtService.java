package com.uber.notification.api.security;

import com.uber.notification.domain.model.RoleName;
import com.uber.notification.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Issues and validates JWTs. Kept in the `api` module (not `infrastructure`) because JWT
 * is specifically an HTTP-boundary authentication concern, not a general infrastructure
 * adapter reused by other layers.
 */
@Component
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                       @Value("${security.jwt.expiration-ms:3600000}") long expirationMillis) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        String roles = user.getRoles().stream().map(Enum::name).collect(Collectors.joining(","));
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMillis)))
                .signWith(signingKey)
                .compact();
    }

    public AuthenticatedPrincipal parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        String rolesClaim = claims.get("roles", String.class);
        Set<RoleName> roles = rolesClaim == null || rolesClaim.isBlank()
                ? Set.of()
                : Set.of(rolesClaim.split(",")).stream().map(RoleName::valueOf).collect(Collectors.toSet());
        return new AuthenticatedPrincipal(userId, email, roles);
    }

    public record AuthenticatedPrincipal(UUID userId, String email, Set<RoleName> roles) {
    }
}
