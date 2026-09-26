package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Administrateurs (rôle {@code ADMIN}) des centres d'une société : c'est le propriétaire de l'application qui les
 * crée et les modifie. Un administrateur est rattaché à un ou plusieurs centres de la société (par exemple à
 * chaque nouveau centre créé) et gère ensuite le personnel de ces centres. Le compte propriétaire, lui, ne peut
 * être créé que par l'installation initiale.
 */
@Service
public class SocieteAdminAccountService {

    private static final Logger log = LoggerFactory.getLogger(SocieteAdminAccountService.class);
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9][a-z0-9._-]{2,49}");
    private static final Pattern EMAIL = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public SocieteAdminAccountService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    public List<Account> list(UUID societeId) {
        requireSociete(societeId);
        Map<UUID, Account> byUser = new LinkedHashMap<>();
        jdbc.query(
                "SELECT u.id, u.username, u.full_name, u.email, u.active, c.id AS center_id, c.name AS center_name "
                        + "FROM app_user u "
                        + "INNER JOIN app_user_center uc ON uc.user_id = u.id "
                        + "INNER JOIN centers c ON c.id = uc.center_id "
                        + "INNER JOIN app_user_role ur ON ur.user_id = u.id "
                        + "INNER JOIN app_role r ON r.id = ur.role_id AND r.code = 'ADMIN' "
                        + "WHERE c.societe_id = ? ORDER BY u.username, c.name",
                (java.sql.ResultSet rs) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    Account account = byUser.get(id);
                    if (account == null) {
                        account = new Account(id, rs.getString("username"), rs.getString("full_name"),
                                rs.getString("email"), rs.getBoolean("active"), new ArrayList<>());
                        byUser.put(id, account);
                    }
                    account.centres().add(new CentreRef(rs.getObject("center_id", UUID.class), rs.getString("center_name")));
                },
                societeId);
        return List.copyOf(byUser.values());
    }

    @Transactional
    public Account create(UUID societeId, UUID centerId, String username, String fullName, String email,
                          String password, String createdBy) {
        requireSociete(societeId);
        String centerName = centreOf(societeId, centerId);
        String login = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(login).matches()) {
            throw new BusinessException("USERNAME_INVALID",
                    "Identifiant invalide (3 à 50 caractères : lettres minuscules, chiffres, point, tiret, souligné)");
        }
        String policy = PasswordPolicy.violation(password, login);
        if (policy != null) {
            throw new BusinessException(policy, "Mot de passe refusé (12 caractères minimum, lettres et chiffres)");
        }
        String mail = email == null || email.isBlank() ? null : email.trim();
        if (mail != null && !EMAIL.matcher(mail).matches()) {
            throw new BusinessException("EMAIL_INVALID", "Adresse email invalide");
        }
        Integer taken = jdbc.queryForObject("SELECT COUNT(1) FROM app_user WHERE username = ?", Integer.class, login);
        if (taken != null && taken > 0) {
            throw new BusinessException("USERNAME_TAKEN", "Cet identifiant est déjà utilisé");
        }
        List<UUID> roles = jdbc.queryForList("SELECT id FROM app_role WHERE code = 'ADMIN'", UUID.class);
        if (roles.isEmpty()) {
            throw new IllegalStateException("Le rôle ADMIN n'existe pas");
        }
        UUID userId = UUID.randomUUID();
        String name = fullName == null || fullName.isBlank() ? login : fullName.trim();
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)",
                userId, login, encoder.encode(password), mail, name);
        jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?, ?)", userId, roles.get(0));
        jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?, ?)", userId, centerId);
        log.info("Administrateur de centre créé : société={}, centre={}, utilisateur={}, par={}",
                societeId, centerId, login, createdBy);
        List<CentreRef> centres = new ArrayList<>();
        centres.add(new CentreRef(centerId, centerName));
        return new Account(userId, login, name, mail, true, centres);
    }

    /**
     * Modifie un administrateur : nom, e-mail et centres de la société auxquels il est rattaché (au moins un). Les
     * rattachements à des centres d'autres sociétés ne sont jamais touchés. Retirer un centre coupe les sessions
     * ouvertes sur ce centre.
     */
    @Transactional
    public Account update(UUID societeId, UUID userId, String fullName, String email, List<UUID> centerIds) {
        requireAdminAccount(societeId, userId);
        String mail = email == null || email.isBlank() ? null : email.trim();
        if (mail != null && !EMAIL.matcher(mail).matches()) {
            throw new BusinessException("EMAIL_INVALID", "Adresse email invalide");
        }
        Set<UUID> wanted = new LinkedHashSet<>(centerIds == null ? List.of() : centerIds);
        if (wanted.isEmpty()) {
            throw new BusinessException("CENTRE_REQUIS", "Un administrateur doit être rattaché à au moins un centre");
        }
        for (UUID centerId : wanted) {
            centreOf(societeId, centerId);
        }
        String username = jdbc.queryForObject("SELECT username FROM app_user WHERE id = ?", String.class, userId);
        String name = fullName == null || fullName.isBlank() ? username : fullName.trim();
        jdbc.update("UPDATE app_user SET full_name = ?, email = ? WHERE id = ?", name, mail, userId);

        List<UUID> current = jdbc.queryForList(
                "SELECT uc.center_id FROM app_user_center uc INNER JOIN centers c ON c.id = uc.center_id "
                        + "WHERE uc.user_id = ? AND c.societe_id = ?", UUID.class, userId, societeId);
        for (UUID centerId : current) {
            if (!wanted.contains(centerId)) {
                jdbc.update("DELETE FROM app_user_center WHERE user_id = ? AND center_id = ?", userId, centerId);
                jdbc.update("UPDATE auth_refresh_token SET revoked = TRUE, revoked_at = CURRENT_TIMESTAMP "
                        + "WHERE user_id = ? AND center_id = ? AND revoked = FALSE", userId, centerId);
            }
        }
        for (UUID centerId : wanted) {
            if (!current.contains(centerId)) {
                jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?, ?)", userId, centerId);
            }
        }
        log.info("Administrateur de centre modifié : société={}, utilisateur={}, centres={}", societeId, username, wanted);
        return list(societeId).stream().filter(a -> a.userId().equals(userId)).findFirst().orElseThrow();
    }

    @Transactional
    public void setActive(UUID societeId, UUID userId, boolean active) {
        requireAdminAccount(societeId, userId);
        jdbc.update("UPDATE app_user SET active = ? WHERE id = ?", active, userId);
        if (!active) {
            revokeSessions(userId);
        }
    }

    @Transactional
    public void resetPassword(UUID societeId, UUID userId, String password) {
        requireAdminAccount(societeId, userId);
        String username = jdbc.queryForObject("SELECT username FROM app_user WHERE id = ?", String.class, userId);
        String policy = PasswordPolicy.violation(password, username);
        if (policy != null) {
            throw new BusinessException(policy, "Mot de passe refusé (12 caractères minimum, lettres et chiffres)");
        }
        jdbc.update("UPDATE app_user SET password_hash = ? WHERE id = ?", encoder.encode(password), userId);
        revokeSessions(userId);
    }

    private void revokeSessions(UUID userId) {
        jdbc.update("UPDATE auth_refresh_token SET revoked = TRUE, revoked_at = CURRENT_TIMESTAMP "
                + "WHERE user_id = ? AND revoked = FALSE", userId);
    }

    private void requireSociete(UUID societeId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(1) FROM societes WHERE id = ?", Integer.class, societeId);
        if (n == null || n == 0) {
            throw new BusinessException("SOCIETE_INTROUVABLE", "Société introuvable");
        }
    }

    /**
     * Le centre doit appartenir à la société ; renvoie son nom.
     */
    private String centreOf(UUID societeId, UUID centerId) {
        if (centerId == null) {
            throw new BusinessException("CENTRE_REQUIS", "Le centre de l'administrateur est obligatoire");
        }
        List<String> names = jdbc.queryForList("SELECT name FROM centers WHERE id = ? AND societe_id = ?",
                String.class, centerId, societeId);
        if (names.isEmpty()) {
            throw new BusinessException("CENTRE_HORS_SOCIETE", "Ce centre n'appartient pas à cette société");
        }
        return names.get(0);
    }

    /** Le compte doit porter le rôle ADMIN et être rattaché à un centre de cette société. */
    private void requireAdminAccount(UUID societeId, UUID userId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(1) FROM app_user_center uc "
                        + "INNER JOIN centers c ON c.id = uc.center_id "
                        + "INNER JOIN app_user_role ur ON ur.user_id = uc.user_id "
                        + "INNER JOIN app_role r ON r.id = ur.role_id AND r.code = 'ADMIN' "
                        + "WHERE c.societe_id = ? AND uc.user_id = ?",
                Integer.class, societeId, userId);
        if (n == null || n == 0) {
            throw new BusinessException("COMPTE_INTROUVABLE", "Administrateur introuvable dans cette société");
        }
    }

    public record CentreRef(UUID id, String name) {
    }

    /** Un administrateur peut être rattaché à plusieurs centres de la société. */
    public record Account(UUID userId, String username, String fullName, String email, boolean active,
                          List<CentreRef> centres) {
    }
}
