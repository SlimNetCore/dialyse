package com.hemodialyse.backend.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationSec;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpirationSec;

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
            Base64.getEncoder().encodeToString(jwtSecret.getBytes())
        );
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate a JWT token from an Authentication object.
     */
    public String generateToken(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        List<String> roles = principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());

        return Jwts.builder()
            .subject(principal.getUsername())
            .claim("userId", principal.getId())
            .claim("roles", roles)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtExpirationSec * 1000))
            .signWith(signingKey())
            .compact();
    }

    /**
     * Generate a JWT from explicit claims (used by AuthService during login).
     */
    public String generateToken(String username, String userId, List<String> roles, String centerId) {
        return Jwts.builder()
            .subject(username)
            .claim("userId", userId)
            .claim("roles", roles)
            .claim("center_id", centerId)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtExpirationSec * 1000))
            .signWith(signingKey())
            .compact();
    }


    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getUserIdFromToken(String token) {
        return parseClaims(token).get("userId", String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        return parseClaims(token).get("roles", List.class);
    }

    public String getCenterIdFromToken(String token) {
        return parseClaims(token).get("center_id", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token JWT expire : {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Token JWT non supporte : {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Token JWT malforme : {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("Erreur JWT : {}", e.getMessage());
        }
        return false;
    }

    public long getExpirationSec() {
        return jwtExpirationSec;
    }

    public long getRefreshExpirationSec() {
        return refreshExpirationSec;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
