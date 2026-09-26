package com.hemodialyse.backend.application.auth;

import com.hemodialyse.backend.infrastructure.security.JwtTokenProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(JdbcTemplate jdbc, JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Connexion à un centre sans précision de société (le centre appartient à exactement une société).
     */
    public LoginResult login(UUID centerId, String username, String password) {
        return login(null, centerId, username, password);
    }

    /**
     * Connexion à un centre. Si {@code societeId} est fourni, le centre doit appartenir à cette société ; dans
     * tous les cas, le centre et sa société doivent être actifs.
     */
    public LoginResult login(UUID societeId, UUID centerId, String username, String password) {
        // 1. Load user from app_user table
        var users = jdbc.queryForList(
            "SELECT id, username, password_hash, full_name, active FROM app_user WHERE username = ?", username
        );
        if (users.isEmpty()) {
            throw new IllegalArgumentException("Identifiants invalides");
        }
        var user = users.get(0);
        boolean active = Boolean.TRUE.equals(user.get("ACTIVE"));
        if (!active) {
            throw new IllegalStateException("Compte utilisateur désactivé");
        }

        // 2. Verify password with BCrypt
        String hash = (String) user.get("PASSWORD_HASH");
        if (!passwordEncoder.matches(password, hash)) {
            throw new IllegalArgumentException("Identifiants invalides");
        }

        UUID userId = (UUID) user.get("ID");

        // 3. Verify user has access to the requested center
        Integer centerAccess = jdbc.queryForObject(
            "SELECT COUNT(1) FROM app_user_center WHERE user_id = ? AND center_id = ?",
            Integer.class, userId, centerId
        );
        if (centerAccess == null || centerAccess == 0) {
            throw new IllegalStateException("Utilisateur non autorisé sur ce centre");
        }

        // 3b. Le centre (et sa société) doit être actif, et appartenir à la société choisie
        assertCentreEtSocieteUtilisables(centerId, societeId);

        // 4. Load center name
        String centerName = jdbc.queryForObject("SELECT name FROM centers WHERE id = ?", String.class, centerId);

        // 5. Load roles for this user
        List<String> roles = jdbc.queryForList(
            "SELECT r.code FROM app_role r INNER JOIN app_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?",
            String.class, userId
        );
        List<String> prefixedRoles = roles.stream().map(r -> "ROLE_" + r).toList();

        // 6. Generate JWT
        String fullName = (String) user.get("FULL_NAME");
        String token = jwtTokenProvider.generateToken(username, userId.toString(), prefixedRoles, centerId.toString());

        return new LoginResult(token, username, fullName, userId, centerId, centerName, prefixedRoles);
    }

    /**
     * Sociétés proposées à la connexion : actives et disposant d'au moins un centre actif.
     */
    public List<DirectoryItem> listLoginSocietes() {
        return jdbc.query(
                "SELECT s.id, s.raison_sociale FROM societes s WHERE s.actif = TRUE AND EXISTS ("
                        + "SELECT 1 FROM centers c WHERE c.societe_id = s.id AND COALESCE(c.actif, TRUE) = TRUE) "
                        + "ORDER BY s.raison_sociale",
                (rs, i) -> new DirectoryItem(rs.getObject("id", UUID.class), rs.getString("raison_sociale")));
    }

    /**
     * Centres actifs d'une société active, proposés à la connexion une fois la société choisie.
     */
    public List<DirectoryItem> listLoginCentres(UUID societeId) {
        return jdbc.query(
                "SELECT c.id, c.name FROM centers c INNER JOIN societes s ON s.id = c.societe_id "
                        + "WHERE c.societe_id = ? AND s.actif = TRUE AND COALESCE(c.actif, TRUE) = TRUE ORDER BY c.name",
                (rs, i) -> new DirectoryItem(rs.getObject("id", UUID.class), rs.getString("name")),
                societeId);
    }

    /**
     * Centres visibles par l'utilisateur connecté : tous pour le SUPERADMIN (éditeur), sinon les centres actifs
     * de sa société (déduite de son centre courant).
     */
    public List<DirectoryItem> listAccessibleCentres(UUID currentCenterId, boolean superAdmin) {
        if (superAdmin) {
            return jdbc.query("SELECT id, name FROM centers ORDER BY name",
                    (rs, i) -> new DirectoryItem(rs.getObject("id", UUID.class), rs.getString("name")));
        }
        return jdbc.query(
                "SELECT c.id, c.name FROM centers c WHERE COALESCE(c.actif, TRUE) = TRUE AND "
                        + "(c.id = ? OR c.societe_id = (SELECT societe_id FROM centers WHERE id = ?)) ORDER BY c.name",
                (rs, i) -> new DirectoryItem(rs.getObject("id", UUID.class), rs.getString("name")),
                currentCenterId, currentCenterId);
    }

    private void assertCentreEtSocieteUtilisables(UUID centerId, UUID expectedSocieteId) {
        var rows = jdbc.query(
                "SELECT c.societe_id, COALESCE(c.actif, TRUE) AS centre_actif, s.actif AS societe_actif "
                        + "FROM centers c LEFT JOIN societes s ON s.id = c.societe_id WHERE c.id = ?",
                (rs, i) -> new Object[]{rs.getObject("societe_id", UUID.class), rs.getBoolean("centre_actif"),
                        rs.getObject("societe_actif") == null || rs.getBoolean("societe_actif")},
                centerId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("Utilisateur non autorisé sur ce centre");
        }
        Object[] row = rows.get(0);
        if (expectedSocieteId != null && !expectedSocieteId.equals(row[0])) {
            throw new IllegalStateException("Ce centre n'appartient pas à la société sélectionnée");
        }
        if (!(Boolean) row[1]) {
            throw new IllegalStateException("Ce centre est désactivé");
        }
        if (!(Boolean) row[2]) {
            throw new IllegalStateException("Cette société est désactivée");
        }
    }

    public LoginResult rebuildSession(UUID centerId, UUID userId) {
        var users = jdbc.queryForList(
                "SELECT username, full_name, active FROM app_user WHERE id = ?",
                userId
        );
        if (users.isEmpty()) {
            throw new IllegalArgumentException("Session invalide");
        }
        var user = users.get(0);
        boolean active = Boolean.TRUE.equals(user.get("ACTIVE"));
        if (!active) {
            throw new IllegalStateException("Compte utilisateur désactivé");
        }

        Integer centerAccess = jdbc.queryForObject(
                "SELECT COUNT(1) FROM app_user_center WHERE user_id = ? AND center_id = ?",
                Integer.class,
                userId,
                centerId
        );
        if (centerAccess == null || centerAccess == 0) {
            throw new IllegalStateException("Utilisateur non autorisé sur ce centre");
        }

        assertCentreEtSocieteUtilisables(centerId, null);

        String username = (String) user.get("USERNAME");
        String fullName = (String) user.get("FULL_NAME");
        String centerName = jdbc.queryForObject("SELECT name FROM centers WHERE id = ?", String.class, centerId);
        List<String> roles = jdbc.queryForList(
                "SELECT r.code FROM app_role r INNER JOIN app_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?",
                String.class,
                userId
        );
        List<String> prefixedRoles = roles.stream().map(r -> "ROLE_" + r).toList();

        return new LoginResult("", username, fullName, userId, centerId, centerName, prefixedRoles);
    }

    /**
     * Entrée d'un annuaire public de connexion : identifiant et nom uniquement (aucune autre donnée exposée).
     */
    public record DirectoryItem(UUID id, String name) {
    }

    public String generateAccessToken(LoginResult session) {
        return jwtTokenProvider.generateToken(
                session.username(),
                session.userId().toString(),
                session.roles(),
                session.centerId().toString()
        );
    }

    public String issueRefreshToken(UUID userId, UUID centerId) {
        String rawToken = generateOpaqueRefreshToken();
        String hash = sha256Hex(rawToken);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusSeconds(jwtTokenProvider.getRefreshExpirationSec());

        jdbc.update(
                "INSERT INTO auth_refresh_token (id, token_hash, user_id, center_id, expires_at, revoked, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                hash,
                userId,
                centerId,
                expiresAt,
                false,
                now
        );
        return rawToken;
    }

    @Transactional
    public RefreshRotationResult rotateFromRefreshToken(String rawToken) {
        RefreshTokenContext context = loadValidRefreshTokenContext(rawToken);

        int updated = jdbc.update(
                "UPDATE auth_refresh_token SET revoked = TRUE, revoked_at = CURRENT_TIMESTAMP WHERE id = ? AND revoked = FALSE",
                context.tokenId()
        );
        if (updated == 0) {
            revokeAllUserTokens(context.userId());
            throw new SecurityException("Refresh token reuse detected");
        }

        LoginResult session = rebuildSession(context.centerId(), context.userId());
        String accessToken = generateAccessToken(session);
        String nextRefreshToken = issueRefreshToken(context.userId(), context.centerId());
        return new RefreshRotationResult(session, accessToken, nextRefreshToken);
    }

    public void revokeRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        jdbc.update(
                "UPDATE auth_refresh_token SET revoked = TRUE, revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ? AND revoked = FALSE",
                sha256Hex(rawToken)
        );
    }

    public void revokeAllUserTokens(UUID userId) {
        jdbc.update(
                "UPDATE auth_refresh_token SET revoked = TRUE, revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked = FALSE",
                userId
        );
    }

    private RefreshTokenContext loadValidRefreshTokenContext(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token manquant");
        }

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, user_id, center_id, expires_at, revoked FROM auth_refresh_token WHERE token_hash = ? FOR UPDATE",
                sha256Hex(rawToken)
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Refresh token invalide");
        }

        Map<String, Object> row = rows.getFirst();
        boolean revoked = Boolean.TRUE.equals(row.get("REVOKED"));
        Instant expiresAt = toInstant(row.get("EXPIRES_AT"));
        if (revoked || expiresAt == null || expiresAt.isBefore(Instant.now())) {
            throw new IllegalArgumentException("Refresh token expiré");
        }

        UUID tokenId = (UUID) row.get("ID");
        UUID userId = (UUID) row.get("USER_ID");
        UUID centerId = (UUID) row.get("CENTER_ID");
        return new RefreshTokenContext(tokenId, userId, centerId);
    }

    private Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof OffsetDateTime odt) return odt.toInstant();
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        return null;
    }

    private String generateOpaqueRefreshToken() {
        byte[] random = new byte[48];
        SECURE_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    public record LoginResult(String token, String username, String fullName, UUID userId, UUID centerId, String centerName, List<String> roles) {
    }

    public record RefreshTokenContext(UUID tokenId, UUID userId, UUID centerId) {
    }

    public record RefreshRotationResult(LoginResult session, String accessToken, String refreshToken) {
    }
}
