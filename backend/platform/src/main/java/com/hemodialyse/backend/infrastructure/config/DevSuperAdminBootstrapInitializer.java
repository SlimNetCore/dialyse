package com.hemodialyse.backend.infrastructure.config;

import com.hemodialyse.backend.infrastructure.security.license.LicenseKeyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Commodité locale/dev uniquement : crée le rôle {@code SUPERADMIN} et un compte {@code superadmin} pour pouvoir
 * essayer la gestion des sociétés sans manipuler la base.
 *
 * <p><b>Désactivé par défaut</b> : le compte propriétaire est normalement créé à la première ouverture de
 * l'application (écran d'installation, {@code InitialSetupService}) avec un mot de passe choisi par son titulaire.
 * Cette commodité ne s'active que sur demande explicite ({@code app.dev.superadmin.enabled=true}) et, comme
 * {@link DevLicenseBootstrapInitializer}, jamais dès que de vraies clés de licence sont configurées.
 */
@Component
public class DevSuperAdminBootstrapInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSuperAdminBootstrapInitializer.class);

    private static final UUID ROLE_ID = UUID.fromString("a0a00001-0000-0000-0000-000000000005");
    private static final UUID USER_ID = UUID.fromString("b0b00001-0000-0000-0000-000000000009");
    private static final String USERNAME = "superadmin";
    private static final String DEV_PASSWORD = "superadmin$$2026dz";

    private final boolean enabled;
    private final LicenseKeyProperties keyProperties;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public DevSuperAdminBootstrapInitializer(@Value("${app.dev.superadmin.enabled:false}") boolean enabled,
                                             LicenseKeyProperties keyProperties, JdbcTemplate jdbc,
                                             PasswordEncoder encoder) {
        this.enabled = enabled;
        this.keyProperties = keyProperties;
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (!enabled || !keyProperties.isEphemeralDevMode()) {
            return;
        }
        if (count("SELECT COUNT(1) FROM app_role WHERE code = 'SUPERADMIN'") == 0) {
            jdbc.update("INSERT INTO app_role (id, code, name, description) VALUES (?, 'SUPERADMIN', ?, ?)",
                    ROLE_ID, "Super administrateur", "Éditeur : sociétés, centres, licences");
        }
        UUID roleId = jdbc.queryForObject("SELECT id FROM app_role WHERE code = 'SUPERADMIN'", UUID.class);

        if (count("SELECT COUNT(1) FROM app_user WHERE username = 'superadmin'") == 0) {
            jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) "
                            + "VALUES (?, ?, ?, ?, ?, TRUE)",
                    USER_ID, USERNAME, encoder.encode(DEV_PASSWORD), "superadmin@hemodialyse.dz",
                    "Super administrateur (dev)");
        }
        UUID userId = jdbc.queryForObject("SELECT id FROM app_user WHERE username = 'superadmin'", UUID.class);

        if (count("SELECT COUNT(1) FROM app_user_role WHERE user_id = ? AND role_id = ?", userId, roleId) == 0) {
            jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        }
        List<UUID> centers = jdbc.queryForList("SELECT id FROM centers", UUID.class);
        for (UUID centerId : centers) {
            if (count("SELECT COUNT(1) FROM app_user_center WHERE user_id = ? AND center_id = ?", userId, centerId) == 0) {
                jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?, ?)", userId, centerId);
            }
        }
        log.warn("[DEV] Compte '{}' disponible (mode clés de licence éphémères uniquement).", USERNAME);
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }
}
