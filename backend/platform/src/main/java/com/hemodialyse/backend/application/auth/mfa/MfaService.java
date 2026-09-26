package com.hemodialyse.backend.application.auth.mfa;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Double authentification TOTP, facultative et par utilisateur : inscription (secret + confirmation par un premier
 * code), vérification à la connexion, désactivation, codes de secours à usage unique.
 * <p>
 * Protections : secret chiffré au repos, un code déjà accepté ne peut pas être rejoué (dernier pas mémorisé),
 * verrouillage temporaire après {@value #MAX_FAILURES} échecs consécutifs.
 */
@Service
public class MfaService {

    static final int MAX_FAILURES = 5;
    static final int LOCK_MINUTES = 5;
    private static final int RECOVERY_CODES = 8;
    private static final String ISSUER = "HemoDialyse";
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final MfaSecretCipher cipher;
    public MfaService(JdbcTemplate jdbc, PasswordEncoder encoder, MfaSecretCipher cipher) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.cipher = cipher;
    }

    private static long now() {
        return java.time.Instant.now().getEpochSecond();
    }

    public boolean isEnabled(UUID userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(1) FROM app_user_mfa WHERE user_id = ? AND enabled = TRUE",
                Integer.class, userId);
        return n != null && n > 0;
    }

    /**
     * Génère un secret en attente de confirmation (remplace une inscription inachevée).
     */
    @Transactional
    public Enrollment beginEnrollment(UUID userId, String username) {
        if (isEnabled(userId)) {
            throw new BusinessException("MFA_ALREADY_ENABLED", "La double authentification est déjà activée");
        }
        String secret = Totp.generateSecret();
        jdbc.update("DELETE FROM app_user_mfa WHERE user_id = ?", userId);
        jdbc.update("INSERT INTO app_user_mfa (user_id, secret_cipher, enabled) VALUES (?, ?, FALSE)",
                userId, cipher.encrypt(secret));
        return new Enrollment(secret, Totp.otpauthUri(ISSUER, username, secret));
    }

    /**
     * Active la double authentification si le code est bon ; renvoie les codes de secours (montrés une seule fois).
     */
    @Transactional
    public List<String> confirmEnrollment(UUID userId, String code) {
        Row row = row(userId).orElseThrow(() ->
                new BusinessException("MFA_NOT_ENROLLED", "Aucune inscription en cours"));
        if (row.enabled) {
            throw new BusinessException("MFA_ALREADY_ENABLED", "La double authentification est déjà activée");
        }
        OptionalLong step = Totp.verify(cipher.decrypt(row.secretCipher), code, now());
        if (step.isEmpty()) {
            throw new BusinessException("MFA_INVALID", "Code invalide");
        }
        jdbc.update("UPDATE app_user_mfa SET enabled = TRUE, last_step = ?, failed_attempts = 0, locked_until = NULL "
                + "WHERE user_id = ?", step.getAsLong(), userId);
        return regenerateRecoveryCodes(userId);
    }

    /**
     * Désactive la double authentification ; exige un code valide (TOTP ou secours).
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void disable(UUID userId, String code) {
        verifySecondFactor(userId, code);
        jdbc.update("DELETE FROM app_user_mfa_recovery WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM app_user_mfa WHERE user_id = ?", userId);
    }

    /**
     * À appeler à la connexion, mot de passe déjà vérifié. Sans effet si l'utilisateur n'a pas activé la double
     * authentification ; sinon exige un code valide.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void requireSecondFactor(UUID userId, String code) {
        if (!isEnabled(userId)) {
            return;
        }
        if (code == null || code.isBlank()) {
            throw new BusinessException("MFA_REQUIRED", "Code de vérification requis");
        }
        verifySecondFactor(userId, code);
    }

    private void verifySecondFactor(UUID userId, String rawCode) {
        Row row = row(userId).filter(r -> r.enabled).orElseThrow(() ->
                new BusinessException("MFA_NOT_ENROLLED", "La double authentification n'est pas activée"));
        if (row.lockedUntil != null && row.lockedUntil.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessException("MFA_LOCKED", "Trop d'échecs : réessayez dans quelques minutes");
        }
        String code = rawCode == null ? "" : rawCode.trim();
        OptionalLong step = Totp.verify(cipher.decrypt(row.secretCipher), code, now());
        if (step.isPresent() && step.getAsLong() > row.lastStep) {
            jdbc.update("UPDATE app_user_mfa SET last_step = ?, failed_attempts = 0, locked_until = NULL WHERE user_id = ?",
                    step.getAsLong(), userId);
            return;
        }
        if (step.isEmpty() && consumeRecoveryCode(userId, code)) {
            jdbc.update("UPDATE app_user_mfa SET failed_attempts = 0, locked_until = NULL WHERE user_id = ?", userId);
            return;
        }
        int failures = row.failedAttempts + 1;
        if (failures >= MAX_FAILURES) {
            jdbc.update("UPDATE app_user_mfa SET failed_attempts = 0, locked_until = ? WHERE user_id = ?",
                    OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(LOCK_MINUTES), userId);
        } else {
            jdbc.update("UPDATE app_user_mfa SET failed_attempts = ? WHERE user_id = ?", failures, userId);
        }
        throw new BusinessException("MFA_INVALID", "Code invalide");
    }

    private boolean consumeRecoveryCode(UUID userId, String code) {
        String normalized = code.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        if (normalized.length() != 10) {
            return false;
        }
        var rows = jdbc.queryForList("SELECT id, code_hash FROM app_user_mfa_recovery WHERE user_id = ? AND used = FALSE",
                userId);
        for (var r : rows) {
            if (encoder.matches(normalized, (String) r.get("CODE_HASH"))) {
                jdbc.update("UPDATE app_user_mfa_recovery SET used = TRUE WHERE id = ?", r.get("ID"));
                return true;
            }
        }
        return false;
    }

    private List<String> regenerateRecoveryCodes(UUID userId) {
        jdbc.update("DELETE FROM app_user_mfa_recovery WHERE user_id = ?", userId);
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 10; j++) {
                sb.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
            }
            String plain = sb.toString();
            jdbc.update("INSERT INTO app_user_mfa_recovery (id, user_id, code_hash, used) VALUES (?, ?, ?, FALSE)",
                    UUID.randomUUID(), userId, encoder.encode(plain));
            codes.add(plain.substring(0, 5) + "-" + plain.substring(5));
        }
        return codes;
    }

    private java.util.Optional<Row> row(UUID userId) {
        var rows = jdbc.query(
                "SELECT secret_cipher, enabled, last_step, failed_attempts, locked_until FROM app_user_mfa WHERE user_id = ?",
                (rs, i) -> new Row(rs.getString("secret_cipher"), rs.getBoolean("enabled"), rs.getLong("last_step"),
                        rs.getInt("failed_attempts"), rs.getObject("locked_until", OffsetDateTime.class)),
                userId);
        return rows.stream().findFirst();
    }

    public record Enrollment(String secret, String otpauthUri) {
    }

    private record Row(String secretCipher, boolean enabled, long lastStep, int failedAttempts,
                       OffsetDateTime lockedUntil) {
    }
}
