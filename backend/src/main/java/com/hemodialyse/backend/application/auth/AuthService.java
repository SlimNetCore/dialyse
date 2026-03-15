package com.hemodialyse.backend.application.auth;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final byte[] secretBytes;
    private final String demoUser;
    private final String demoPassword;

    public AuthService(
        JdbcTemplate jdbcTemplate,
        @Value("${app.security.jwt.secret}") String secret,
        @Value("${app.security.auth.demo-user:admin}") String demoUser,
        @Value("${app.security.auth.demo-password:admin123}") String demoPassword
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.demoUser = demoUser;
        this.demoPassword = demoPassword;
    }

    public LoginResult login(UUID centerId, String username, String password) {
        if (!demoUser.equals(username) || !demoPassword.equals(password)) {
            throw new IllegalArgumentException("Identifiants invalides");
        }

        Integer assignment = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM user_center_assignment WHERE user_id = ? AND center_id = ?",
            Integer.class,
            username,
            centerId
        );

        if (assignment == null || assignment == 0) {
            throw new IllegalStateException("Utilisateur non autorise sur ce centre");
        }

        String centerName = jdbcTemplate.queryForObject(
            "SELECT name FROM centers WHERE id = ?",
            String.class,
            centerId
        );

        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("hemodialyse-backend")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(60 * 60 * 8)))
                .subject(username)
                .claim("center_id", centerId.toString())
                .claim("roles", List.of("ADMIN"))
                .build();

            JWSHeader header = new JWSHeader(JWSAlgorithm.HS256);
            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new MACSigner(secretBytes));

            String token = signedJWT.serialize();
            return new LoginResult(token, username, centerId, centerName);
        } catch (JOSEException e) {
            throw new RuntimeException("Erreur lors de la generation du token JWT", e);
        }
    }

    public record LoginResult(String token, String username, UUID centerId, String centerName) {
    }
}
