package com.hemodialyse.backend.application.license;

import com.hemodialyse.backend.infrastructure.security.license.LicenseKeyProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Signs and verifies license tokens (compact RS256 JWTs, same library already used
 * for session auth in {@code JwtTokenProvider}, different keypair). Signing requires
 * the private key and only succeeds on the vendor's own "authority" instance;
 * verification only needs the public key and works on every deployed instance.
 */
@Component
public class LicenseTokenService {

    private final LicenseKeyProperties keys;

    public LicenseTokenService(LicenseKeyProperties keys) {
        this.keys = keys;
    }

    public IssuedToken issue(UUID centerId, String centerCode, String type, int maxUsers,
                             Instant validFrom, Instant validUntil) {
        if (!keys.isAuthority()) {
            throw new IllegalStateException(
                    "Cette instance ne détient pas la clé privée de licence — seule l'instance autorité peut émettre des licences.");
        }
        String jti = UUID.randomUUID().toString();
        String compact = Jwts.builder()
                .id(jti)
                .subject(centerId.toString())
                .claim("centerCode", centerCode)
                .claim("type", type)
                .claim("maxUsers", maxUsers)
                .issuedAt(Date.from(Instant.now()))
                .notBefore(Date.from(validFrom))
                .expiration(Date.from(validUntil))
                .signWith(keys.privateKey(), Jwts.SIG.RS256)
                .compact();
        return new IssuedToken(compact, jti);
    }

    public LicenseClaims verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(keys.publicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new LicenseClaims(
                    UUID.fromString(claims.getSubject()),
                    claims.get("centerCode", String.class),
                    claims.get("type", String.class),
                    claims.get("maxUsers", Integer.class),
                    claims.getId(),
                    claims.getNotBefore() != null ? claims.getNotBefore().toInstant() : null,
                    claims.getExpiration().toInstant()
            );
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidLicenseException("Licence invalide, expirée ou signature incorrecte : " + e.getMessage(), e);
        }
    }

    public record IssuedToken(String compact, String jti) {
    }

    public record LicenseClaims(UUID centerId, String centerCode, String type, int maxUsers,
                                String jti, Instant notBefore, Instant expiresAt) {
    }
}
