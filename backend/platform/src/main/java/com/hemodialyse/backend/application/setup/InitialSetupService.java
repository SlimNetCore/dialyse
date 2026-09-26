package com.hemodialyse.backend.application.setup;

import com.hemodialyse.backend.application.direction.PasswordPolicy;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Installation initiale : à la première ouverture de l'application, aucun compte propriétaire (SUPERADMIN)
 * n'existe et l'interface propose d'en créer un avec un mot de passe exigeant. Dès qu'un propriétaire existe,
 * l'opération est définitivement refusée : nul ne peut plus créer de SUPERADMIN par ce chemin, et aucun autre
 * chemin de l'application n'en crée.
 * <p>
 * Un jeton d'installation facultatif ({@code SETUP_TOKEN}) protège une instance exposée avant sa configuration.
 */
@Service
public class InitialSetupService {

    private static final Logger log = LoggerFactory.getLogger(InitialSetupService.class);
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,49}");
    private static final Pattern EMAIL = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final UUID ROLE_ID = UUID.fromString("a0a00001-0000-0000-0000-000000000005");
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final String setupToken;
    public InitialSetupService(JdbcTemplate jdbc, PasswordEncoder encoder, TransactionTemplate tx,
                               @Value("${app.setup.token:}") String setupToken) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.tx = tx;
        this.setupToken = setupToken == null ? "" : setupToken.trim();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public Status status() {
        boolean required = !ownerExists();
        return new Status(required, required && !setupToken.isEmpty());
    }

    /**
     * Crée le compte propriétaire. Sérialisé : deux demandes simultanées ne peuvent pas créer deux propriétaires.
     */
    public synchronized void createOwner(String username, String fullName, String email, String password,
                                         String token) {
        tx.executeWithoutResult(status -> {
            if (ownerExists()) {
                throw new BusinessException("SETUP_ALREADY_DONE", "L'installation est déjà configurée");
            }
            if (!setupToken.isEmpty() && !constantTimeEquals(setupToken, token == null ? "" : token.trim())) {
                throw new BusinessException("SETUP_TOKEN_INVALID", "Jeton d'installation invalide");
            }
            String login = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
            if (!USERNAME.matcher(login).matches()) {
                throw new BusinessException("USERNAME_INVALID",
                        "Identifiant invalide (3 à 50 caractères : lettres minuscules, chiffres, point, tiret, souligné)");
            }
            String policy = PasswordPolicy.ownerViolation(password, login);
            if (policy != null) {
                throw new BusinessException(policy, "Mot de passe refusé");
            }
            String mail = email == null || email.isBlank() ? null : email.trim();
            if (mail != null && !EMAIL.matcher(mail).matches()) {
                throw new BusinessException("EMAIL_INVALID", "Adresse email invalide");
            }
            Integer taken = jdbc.queryForObject("SELECT COUNT(1) FROM app_user WHERE username = ?", Integer.class, login);
            if (taken != null && taken > 0) {
                throw new BusinessException("USERNAME_TAKEN", "Cet identifiant est déjà utilisé");
            }
            UUID roleId = ensureRole();
            UUID userId = UUID.randomUUID();
            String name = fullName == null || fullName.isBlank() ? login : fullName.trim();
            jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)",
                    userId, login, encoder.encode(password), mail, name);
            jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
            log.info("Installation initiale : compte propriétaire '{}' créé", login);
        });
    }

    private boolean ownerExists() {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(1) FROM app_user_role ur INNER JOIN app_role r ON r.id = ur.role_id "
                        + "WHERE r.code = 'SUPERADMIN'", Integer.class);
        return n != null && n > 0;
    }

    private UUID ensureRole() {
        var ids = jdbc.queryForList("SELECT id FROM app_role WHERE code = 'SUPERADMIN'", UUID.class);
        if (!ids.isEmpty()) {
            return ids.get(0);
        }
        jdbc.update("INSERT INTO app_role (id, code, name, description) VALUES (?, 'SUPERADMIN', ?, ?)",
                ROLE_ID, "Propriétaire de l'application", "Éditeur : sociétés, centres, licences");
        return ROLE_ID;
    }

    public record Status(boolean required, boolean tokenRequired) {
    }
}
