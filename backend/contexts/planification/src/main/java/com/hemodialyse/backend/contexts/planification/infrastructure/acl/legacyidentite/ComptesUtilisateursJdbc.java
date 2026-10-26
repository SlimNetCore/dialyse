package com.hemodialyse.backend.contexts.planification.infrastructure.acl.legacyidentite;

import com.hemodialyse.backend.contexts.planification.application.ComptesUtilisateurs;
import com.hemodialyse.backend.contexts.planification.domain.AdresseEmail;
import com.hemodialyse.backend.contexts.planification.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.planification.domain.IdentifiantDeConnexion;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.planification.domain.Identifiants.CompteId;
import com.hemodialyse.backend.contexts.planification.domain.PageDeResultats;
import com.hemodialyse.backend.contexts.planification.domain.Pagination;
import com.hemodialyse.backend.contexts.planification.domain.RegleDePlanificationViolee;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ACL vers les comptes utilisateurs (contexte Identité et accès, tables {@code app_user}, {@code app_user_role},
 * {@code app_user_center}, {@code app_role} : mêmes tables que l'administration des utilisateurs). Traduit un compte en
 * {@link Compte} du contexte ; retirée quand Identité et accès sera extrait.
 */
public class ComptesUtilisateursJdbc implements ComptesUtilisateurs {

    private static final String ROLE_INFIRMIER = "INFIRMIER";

    private static final RowMapper<Compte> MAPPER = (rs, i) -> new Compte(new CompteId(rs.getObject("id", UUID.class)),
            rs.getString("username"), rs.getString("full_name"), rs.getBoolean("active"));

    /** Comptes rattachés au centre (premier paramètre) et dotés du rôle INFIRMIER. */
    private static final String COMPTES_INFIRMIER_DU_CENTRE =
            "FROM app_user u "
                    + "JOIN app_user_center uc ON uc.user_id = u.id AND uc.center_id = ? "
                    + "WHERE EXISTS (SELECT 1 FROM app_user_role ur JOIN app_role r ON r.id = ur.role_id "
                    + "WHERE ur.user_id = u.id AND r.code = '" + ROLE_INFIRMIER + "')";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public ComptesUtilisateursJdbc(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Optional<Compte> compteInfirmierDuCentre(CentreId centre, CompteId compte) {
        // le compte est verrouillé AVANT que son état soit lu (ordre global : centre, fiches, app_user ; ADR 0056) : une
        // désactivation concurrente ne peut pas passer entre la lecture de l'état et la liaison
        RefusDeVerrouDuCompte.verrouillant(() -> jdbc.queryForList("SELECT id FROM app_user WHERE id = ? FOR UPDATE", compte.valeur()));
        return jdbc.query("SELECT u.id, u.username, u.full_name, u.active " + COMPTES_INFIRMIER_DU_CENTRE + " AND u.id = ?",
                MAPPER, centre.valeur(), compte.valeur()).stream().findFirst();
    }

    @Override
    public List<Compte> parIds(Collection<CompteId> comptes) {
        if (comptes.isEmpty()) {
            return List.of();
        }
        String marques = comptes.stream().map(id -> "?").collect(Collectors.joining(", "));
        return jdbc.query("SELECT id, username, full_name, active FROM app_user WHERE id IN (" + marques + ")",
                MAPPER, comptes.stream().map(CompteId::valeur).toArray());
    }

    @Override
    public PageDeResultats<Compte> comptesLiables(CentreId centre, Pagination pagination) {
        String libres = COMPTES_INFIRMIER_DU_CENTRE
                + " AND NOT EXISTS (SELECT 1 FROM infirmier i WHERE i.center_id = uc.center_id AND i.user_id = u.id)";
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + libres, Long.class, centre.valeur());
        List<Compte> comptes = jdbc.query("SELECT u.id, u.username, u.full_name, u.active " + libres
                + " ORDER BY u.username LIMIT ? OFFSET ?", MAPPER, centre.valeur(), pagination.taille(), pagination.decalage());
        return new PageDeResultats<>(comptes, total == null ? 0 : total, pagination.page(), pagination.taille());
    }

    @Override
    public boolean identifiantExiste(IdentifiantDeConnexion identifiant) {
        Integer nombre = jdbc.queryForObject("SELECT COUNT(1) FROM app_user WHERE LOWER(username) = LOWER(?)",
                Integer.class, identifiant.valeur());
        return nombre != null && nombre > 0;
    }

    @Override
    public Compte creerCompteInfirmier(CentreId centre, IdentifiantDeConnexion identifiant, String motDePasseTemporaire,
                                       String nomComplet, AdresseEmail email) {
        UUID role = jdbc.query("SELECT id FROM app_role WHERE code = ?", (rs, i) -> rs.getObject(1, UUID.class), ROLE_INFIRMIER)
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Le rôle " + ROLE_INFIRMIER + " n'existe pas"));
        verrouillerLesIdentifiants();
        if (identifiantExiste(identifiant)) {
            throw new RegleDePlanificationViolee("COMPTE_IDENTIFIANT_EXISTANT", "Cet identifiant de connexion existe déjà");
        }
        UUID id = UUID.randomUUID();
        try {
            // mot de passe temporaire : l'infirmier devra le remplacer dès sa première connexion
            jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active, must_change_password) "
                            + "VALUES (?,?,?,?,?,?,?)",
                    id, identifiant.valeur(), passwordEncoder.encode(motDePasseTemporaire),
                    email == null ? null : email.valeur(), nomComplet, true, true);
        } catch (DuplicateKeyException e) {
            // l'identifiant a été pris entre le contrôle et l'insertion (autre centre : pas le même verrou)
            throw new RegleDePlanificationViolee("COMPTE_IDENTIFIANT_EXISTANT", "Cet identifiant de connexion existe déjà");
        }
        jdbc.update("INSERT INTO app_user_role (user_id, role_id) VALUES (?,?)", id, role);
        jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?,?)", id, centre.valeur());
        return new Compte(new CompteId(id), identifiant.valeur(), nomComplet, true);
    }

    /**
     * Sur PostgreSQL, {@code app_user.username} n'a AUCUNE contrainte d'unicité (schéma Flyway, KB-PLN-24) : deux créations
     * simultanées du même identifiant, dans deux centres (donc sous deux verrous de centre différents), passeraient toutes deux. Un
     * verrou consultatif de la transaction les sérialise ; le contrôle est rejoué une fois le verrou pris. Le refus du verrou dans le
     * délai de la transaction (SQLSTATE 55P03) est une écriture concurrente. H2 a la contrainte {@code UNIQUE}.
     */
    @Override
    public void verrouillerLesIdentifiants() {
        boolean postgreSql = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connexion ->
                "PostgreSQL".equalsIgnoreCase(connexion.getMetaData().getDatabaseProductName())));
        if (!postgreSql) {
            return;
        }
        try {
            jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", rs -> {
            }, "planification.compte.identifiant");
        } catch (UncategorizedSQLException e) {
            if (e.getSQLException() != null && "55P03".equals(e.getSQLException().getSQLState())) {
                throw new EcritureConcurrente();
            }
            throw e;
        }
    }

    @Override
    public void definirActif(CompteId compte, boolean actif) {
        RefusDeVerrouDuCompte.verrouillant(() -> jdbc.update("UPDATE app_user SET active = ? WHERE id = ?", actif, compte.valeur()));
    }
}
