package com.hemodialyse.backend.infrastructure.persistence.adapter.infirmier;

import com.hemodialyse.backend.domain.infirmier.port.ComptesInfirmierPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Comptes utilisateurs vus depuis le personnel soignant, sur les tables {@code app_user}, {@code app_user_role} et
 * {@code app_user_center} (mêmes tables que l'administration des utilisateurs).
 */
@Component
public class ComptesInfirmierJdbcAdapter implements ComptesInfirmierPort {

    private static final String ROLE_INFIRMIER = "INFIRMIER";

    private static final RowMapper<CompteRef> MAPPER = (rs, i) -> new CompteRef(
            rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("full_name"), rs.getBoolean("active"));

    /**
     * Comptes rattachés au centre (premier paramètre) et dotés du rôle INFIRMIER.
     */
    private static final String COMPTES_INFIRMIER_DU_CENTRE =
            "FROM app_user u "
                    + "JOIN app_user_center uc ON uc.user_id = u.id AND uc.center_id = ? "
                    + "WHERE EXISTS (SELECT 1 FROM app_user_role ur JOIN app_role r ON r.id = ur.role_id "
                    + "WHERE ur.user_id = u.id AND r.code = '" + ROLE_INFIRMIER + "')";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public ComptesInfirmierJdbcAdapter(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Optional<CompteRef> trouverCompteInfirmier(UUID centerId, UUID userId) {
        return jdbc.query("SELECT u.id, u.username, u.full_name, u.active " + COMPTES_INFIRMIER_DU_CENTRE
                + " AND u.id = ?", MAPPER, centerId, userId).stream().findFirst();
    }

    @Override
    public List<CompteRef> trouverParIds(Collection<UUID> userIds) {
        if (userIds.isEmpty()) return List.of();
        String marques = userIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        return jdbc.query("SELECT id, username, full_name, active FROM app_user WHERE id IN (" + marques + ")",
                MAPPER, userIds.toArray());
    }

    @Override
    public PagedResult<CompteRef> comptesLiables(UUID centerId, int page, int size) {
        String libres = COMPTES_INFIRMIER_DU_CENTRE
                + " AND NOT EXISTS (SELECT 1 FROM infirmier i WHERE i.center_id = uc.center_id AND i.user_id = u.id)";
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + libres, Long.class, centerId);
        List<CompteRef> items = jdbc.query("SELECT u.id, u.username, u.full_name, u.active " + libres
                + " ORDER BY u.username LIMIT ? OFFSET ?", MAPPER, centerId, size, (long) page * size);
        return PagedResult.of(items, total == null ? 0 : total, page, size);
    }

    @Override
    public boolean identifiantExiste(String username) {
        Integer count = jdbc.queryForObject("SELECT COUNT(1) FROM app_user WHERE LOWER(username) = LOWER(?)",
                Integer.class, username);
        return count != null && count > 0;
    }

    @Override
    public CompteRef creerCompteInfirmier(UUID centerId, String username, String motDePasse, String nomComplet,
                                          String email) {
        UUID roleId = jdbc.query("SELECT id FROM app_role WHERE code = ?",
                        (rs, i) -> rs.getObject(1, UUID.class), ROLE_INFIRMIER).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Le rôle " + ROLE_INFIRMIER + " n'existe pas"));
        UUID id = UUID.randomUUID();
        // mot de passe temporaire : l'infirmier devra le remplacer dès sa première connexion
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active, must_change_password) "
                        + "VALUES (?,?,?,?,?,?,?)",
                id, username, passwordEncoder.encode(motDePasse), email, nomComplet, true, true);
        jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?,?)", id, roleId);
        jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?,?)", id, centerId);
        return new CompteRef(id, username, nomComplet, true);
    }

    @Override
    public void definirActif(UUID userId, boolean actif) {
        jdbc.update("UPDATE app_user SET active = ? WHERE id = ?", actif, userId);
    }
}
