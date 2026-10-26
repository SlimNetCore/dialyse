package com.hemodialyse.backend.characterization.planification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * Harnais de la slice P2 (référentiel des infirmiers et comptes liés), au-dessus du harnais commun de Planification.
 * <p>
 * Fixtures supplémentaires : comptes utilisateurs (préfixe d'identifiant {@code p2.}, tous nettoyés), roulement d'un
 * infirmier (lecture seule : la slice P3 le verrouille), lectures SQL normalisées des tables {@code infirmier},
 * {@code app_user}, {@code app_user_role} et {@code app_user_center}. Le mot de passe temporaire est masqué dans les goldens
 * (valeur aléatoire) et vérifié à part par des assertions de forme.
 * <p>
 * Placé dans le même paquet que le harnais commun pour accéder à {@code Resp} (record protégé).
 */
public abstract class InfirmiersCharacterizationSupport extends PlanificationCharacterizationSupport {

    protected static final String INFIRMIERS = "/api/v1/infirmiers";
    protected static final String UTILISATEURS = "/api/v1/users";
    protected static final String PREFIXE = "p2.";
    protected static final String MDP_MASQUE = "<MOT_DE_PASSE_TEMPORAIRE>";

    @Autowired
    protected PasswordEncoder passwordEncoder;
    private int sequenceComptes = 0;

    @BeforeEach
    protected void setUpInfirmiers() {
        nettoyerInfirmiers();
        sequenceComptes = 0;
        assurerLesCentres();
    }

    @AfterEach
    protected void apresInfirmiers() {
        nettoyerInfirmiers();
        jdbc.update("DELETE FROM centers WHERE id IN (?, ?)", CENTRE_A, CENTRE_B);
    }

    /** {@code app_user_center} référence {@code centers} : les deux centres de test doivent exister. */
    protected void assurerLesCentres() {
        jdbc.update("MERGE INTO centers (id, code, name, societe_id, actif) KEY (id) VALUES (?, 'P2-A', 'Centre P2 A', ?, TRUE)",
                CENTRE_A, SOCIETE);
        jdbc.update("MERGE INTO centers (id, code, name, societe_id, actif) KEY (id) VALUES (?, 'P2-B', 'Centre P2 B', ?, TRUE)",
                CENTRE_B, SOCIETE);
    }

    protected void nettoyerInfirmiers() {
        jdbc.update("DELETE FROM app_user_role WHERE user_id IN (SELECT id FROM app_user WHERE LOWER(username) LIKE 'p2.%')");
        jdbc.update("DELETE FROM app_user_center WHERE user_id IN (SELECT id FROM app_user WHERE LOWER(username) LIKE 'p2.%')");
        jdbc.update("DELETE FROM app_user WHERE LOWER(username) LIKE 'p2.%'");
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM infirmier_affectation WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM infirmier WHERE center_id = ?", centre);
        }
    }

    // ------------------------------------------------------------------ fixtures

    /** Infirmier complet (matricule libre, téléphone, isolement, compte). */
    protected UUID fiche(UUID centre, String alias, String matricule, String nom, String prenom, String telephone,
                         String qualification, boolean isolement, boolean actif, UUID userId) {
        UUID id = UUID.nameUUIDFromBytes(("planification-p2-" + alias).getBytes(StandardCharsets.UTF_8));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO infirmier (id, center_id, matricule, nom, prenom, telephone, qualification, "
                        + "habilite_isolement, actif, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, centre, matricule, nom, prenom, telephone, qualification, isolement, actif, userId);
        return id;
    }

    protected UUID fiche(UUID centre, String alias, String matricule, String nom, String prenom, boolean actif) {
        return fiche(centre, alias, matricule, nom, prenom, null, "INFIRMIER", false, actif, null);
    }

    /** Compte utilisateur déjà existant (identifiant {@code p2.<suffixe>}), rattaché aux centres donnés avec un rôle. */
    protected UUID compte(String alias, String suffixe, String nomComplet, String role, boolean actif, UUID... centres) {
        assurerLesCentres();
        UUID id = UUID.nameUUIDFromBytes(("planification-p2-compte-" + alias + "-" + (++sequenceComptes))
                .getBytes(StandardCharsets.UTF_8));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?, ?, 'x', NULL, ?, ?)",
                id, PREFIXE + suffixe, nomComplet, actif);
        if (role != null) {
            jdbc.update("INSERT INTO app_user_role (user_id, role_id) SELECT ?, id FROM app_role WHERE code = ?", id, role);
        }
        for (UUID centre : centres) {
            jdbc.update("INSERT INTO app_user_center (user_id, center_id) VALUES (?, ?)", id, centre);
        }
        return id;
    }

    /** Roulement d'un infirmier (une ligne, lue par la liste du référentiel). */
    protected void affectation(UUID centre, String alias, UUID infirmier, UUID salle, UUID creneau, String jours) {
        UUID id = UUID.nameUUIDFromBytes(("planification-p2-affectation-" + alias).getBytes(StandardCharsets.UTF_8));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO infirmier_affectation (id, center_id, infirmier_id, salle_id, creneau_id, jours) "
                + "VALUES (?, ?, ?, ?, ?, ?)", id, centre, infirmier, salle, creneau, jours);
    }

    // ------------------------------------------------------------------ requêtes

    protected Resp doDelete(String path, RequestPostProcessor who) throws Exception {
        return send(delete(java.net.URI.create(path)).with(who));
    }

    /** Masque le mot de passe temporaire (aléatoire) dans le corps de la réponse. */
    protected Resp sansMotDePasse(Resp r) {
        return new Resp(r.status(), r.contentType(),
                r.raw() == null ? null : r.raw().replaceAll("\"motDePasseTemporaire\"\\s*:\\s*\"[^\"]*\"",
                        "\"motDePasseTemporaire\":\"" + MDP_MASQUE + "\""),
                r.cacheControl());
    }

    protected String motDePasse(Resp r) {
        return corps(r).path("motDePasseTemporaire").asString();
    }

    // ------------------------------------------------------------------ observation de la base

    protected void etatInfirmiers(String name) {
        goldenSql(name, "SELECT center_id, matricule, nom, prenom, telephone, qualification, habilite_isolement, actif, user_id "
                + "FROM infirmier WHERE center_id IN ('" + CENTRE_A + "','" + CENTRE_B + "') ORDER BY center_id, matricule");
    }

    protected void etatComptes(String name) {
        goldenSql(name, "SELECT u.username, u.full_name, u.email, u.active, u.must_change_password, "
                + "(u.password_hash LIKE '$2%') AS mot_de_passe_hache, "
                + "COALESCE((SELECT LISTAGG(r.code, ',') WITHIN GROUP (ORDER BY r.code) FROM app_user_role ur "
                + "JOIN app_role r ON r.id = ur.role_id WHERE ur.user_id = u.id), '') AS roles, "
                + "COALESCE((SELECT LISTAGG(CAST(uc.center_id AS VARCHAR), ',') WITHIN GROUP (ORDER BY uc.center_id) "
                + "FROM app_user_center uc WHERE uc.user_id = u.id), '') AS centres "
                + "FROM app_user u WHERE LOWER(u.username) LIKE 'p2.%' ORDER BY u.username");
    }

    protected int nombreDeFiches() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM infirmier WHERE center_id IN (?, ?)", Integer.class, CENTRE_A, CENTRE_B);
    }

    protected int nombreDeComptes() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE LOWER(username) LIKE 'p2.%'", Integer.class);
    }

    /** Empreinte de toutes les données de la slice : sert à dire si une requête a écrit quelque chose. */
    protected String empreinte() {
        return jdbc.queryForList("SELECT matricule, nom, prenom, telephone, qualification, habilite_isolement, actif, user_id "
                        + "FROM infirmier WHERE center_id IN (?, ?) ORDER BY center_id, matricule", CENTRE_A, CENTRE_B).toString()
                + jdbc.queryForList("SELECT username, full_name, email, active FROM app_user WHERE LOWER(username) LIKE 'p2.%' "
                + "ORDER BY username").toString();
    }
}
