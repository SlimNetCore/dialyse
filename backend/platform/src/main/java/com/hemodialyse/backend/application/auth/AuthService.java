package com.hemodialyse.backend.application.auth;

import com.hemodialyse.backend.application.auth.mfa.MfaService;
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
    private final MfaService mfaService;

    public AuthService(JdbcTemplate jdbc, JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder,
                       MfaService mfaService) {
        this.jdbc = jdbc;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.mfaService = mfaService;
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
        return login(societeId, centerId, username, password, null);
    }

    /**
     * Connexion à un centre avec code de double authentification ({@code otp}, requis seulement si l'utilisateur
     * l'a activée ; vérifié après le mot de passe).
     */
    public LoginResult login(UUID societeId, UUID centerId, String username, String password, String otp) {
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

        // 2b. Double authentification (si activée par l'utilisateur), après le mot de passe
        mfaService.requireSecondFactor(userId, otp);

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

    // ───────────────────── Sessions sans centre (direction, propriétaire) ─────────────────────

    /**
     * Connexion sans centre :
     * <ul>
     *   <li>{@code societeId} absent → session <b>PLATEFORME</b> du propriétaire (rôle SUPERADMIN requis) ;</li>
     *   <li>{@code societeId} présent → session <b>SOCIETE</b> de la direction (rôle DIRECTION requis, compte rattaché
     *       à cette société, société active).</li>
     * </ul>
     * Le mot de passe est vérifié avant tout contrôle de rôle, pour ne rien révéler à un tiers.
     */
    public LoginResult loginScoped(UUID societeId, String username, String password) {
        return loginScoped(societeId, username, password, null);
    }

    /**
     * Connexion sans centre avec code de double authentification ({@code otp}, requis seulement si activée).
     */
    public LoginResult loginScoped(UUID societeId, String username, String password, String otp) {
        var users = jdbc.queryForList(
                "SELECT id, username, password_hash, full_name, active FROM app_user WHERE username = ?", username);
        if (users.isEmpty()) {
            throw new IllegalArgumentException("Identifiants invalides");
        }
        var user = users.get(0);
        if (!passwordEncoder.matches(password, (String) user.get("PASSWORD_HASH"))) {
            throw new IllegalArgumentException("Identifiants invalides");
        }
        if (!Boolean.TRUE.equals(user.get("ACTIVE"))) {
            throw new IllegalStateException("Compte utilisateur désactivé");
        }
        UUID userId = (UUID) user.get("ID");
        mfaService.requireSecondFactor(userId, otp);
        LoginResult session = buildScopedSession(societeId, userId, username, (String) user.get("FULL_NAME"));
        return new LoginResult(generateAccessToken(session), session.username(), session.fullName(), session.userId(),
                null, null, session.roles(), session.societeId(), session.societeName(), session.scope());
    }

    /**
     * Reconstitue une session sans centre (rafraîchissement, {@code /auth/me}) en revérifiant droits et état.
     */
    public LoginResult rebuildScopedSession(UUID societeId, UUID userId) {
        var users = jdbc.queryForList("SELECT username, full_name, active FROM app_user WHERE id = ?", userId);
        if (users.isEmpty()) {
            throw new IllegalArgumentException("Session invalide");
        }
        var user = users.get(0);
        if (!Boolean.TRUE.equals(user.get("ACTIVE"))) {
            throw new IllegalStateException("Compte utilisateur désactivé");
        }
        return buildScopedSession(societeId, userId, (String) user.get("USERNAME"), (String) user.get("FULL_NAME"));
    }

    private LoginResult buildScopedSession(UUID societeId, UUID userId, String username, String fullName) {
        List<String> roles = jdbc.queryForList(
                "SELECT r.code FROM app_role r INNER JOIN app_user_role ur ON ur.role_id = r.id WHERE ur.user_id = ?",
                String.class, userId);
        List<String> prefixed = roles.stream().map(r -> "ROLE_" + r).toList();
        if (societeId == null) {
            if (!roles.contains("SUPERADMIN")) {
                throw new IllegalStateException("Sélectionnez votre société pour accéder à l'espace direction");
            }
            return new LoginResult("", username, fullName, userId, null, null, prefixed, null, null, "PLATEFORME");
        }
        if (!roles.contains("DIRECTION")) {
            throw new IllegalStateException("Ce compte n'a pas accès à l'espace direction");
        }
        Integer assigned = jdbc.queryForObject(
                "SELECT COUNT(1) FROM app_user_societe WHERE user_id = ? AND societe_id = ?", Integer.class, userId,
                societeId);
        if (assigned == null || assigned == 0) {
            throw new IllegalStateException("Ce compte n'est pas rattaché à cette société");
        }
        var societe = jdbc.queryForList("SELECT raison_sociale, actif FROM societes WHERE id = ?", societeId);
        if (societe.isEmpty() || !Boolean.TRUE.equals(societe.get(0).get("ACTIF"))) {
            throw new IllegalStateException("Cette société est désactivée");
        }
        return new LoginResult("", username, fullName, userId, null, null, prefixed, societeId,
                (String) societe.get(0).get("RAISON_SOCIALE"), "SOCIETE");
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
                session.centerId() == null ? null : session.centerId().toString(),
                session.societeId() == null ? null : session.societeId().toString()
        );
    }

    public String issueRefreshToken(UUID userId, UUID centerId) {
        return issueRefreshToken(userId, centerId, null);
    }

    public String issueRefreshToken(UUID userId, UUID centerId, UUID societeId) {
        String rawToken = generateOpaqueRefreshToken();
        String hash = sha256Hex(rawToken);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusSeconds(jwtTokenProvider.getRefreshExpirationSec());

        jdbc.update(
                "INSERT INTO auth_refresh_token (id, token_hash, user_id, center_id, societe_id, expires_at, revoked, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                hash,
                userId,
                centerId,
                societeId,
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

        LoginResult session = context.centerId() != null
                ? rebuildSession(context.centerId(), context.userId())
                : rebuildScopedSession(context.societeId(), context.userId());
        String accessToken = generateAccessToken(session);
        String nextRefreshToken = issueRefreshToken(context.userId(), context.centerId(), context.societeId());
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
                "SELECT id, user_id, center_id, societe_id, expires_at, revoked FROM auth_refresh_token WHERE token_hash = ? FOR UPDATE",
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
        return new RefreshTokenContext(tokenId, userId, centerId, (UUID) row.get("SOCIETE_ID"));
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

    /**
     * Résultat d'une connexion. {@code scope} vaut CENTRE (session d'un centre), SOCIETE (direction, sans centre) ou
     * PLATEFORME (propriétaire, sans centre ni société).
     */
    public record LoginResult(String token, String username, String fullName, UUID userId, UUID centerId,
                              String centerName, List<String> roles, UUID societeId, String societeName, String scope) {
        public LoginResult(String token, String username, String fullName, UUID userId, UUID centerId,
                           String centerName, List<String> roles) {
            this(token, username, fullName, userId, centerId, centerName, roles, null, null, "CENTRE");
        }
    }

    public record RefreshTokenContext(UUID tokenId, UUID userId, UUID centerId, UUID societeId) {
        public RefreshTokenContext(UUID tokenId, UUID userId, UUID centerId) {
            this(tokenId, userId, centerId, null);
        }
    }

    public record RefreshRotationResult(LoginResult session, String accessToken, String refreshToken) {
    }
}
