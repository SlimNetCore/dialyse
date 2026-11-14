package com.hemodialyse.backend.characterization.identite.idnlecture;

import com.hemodialyse.backend.infrastructure.config.DevSuperAdminBootstrapInitializer;
import com.hemodialyse.backend.infrastructure.security.license.LicenseKeyProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * IDN-lecture, amorçage de développement du compte {@code superadmin} ({@code DevSuperAdminBootstrapInitializer}) : désactivé par défaut,
 * sans effet avec de vraies clés de licence, sinon création idempotente du rôle, du compte et de son rattachement à TOUS les centres
 * (actifs ou non). Le mot de passe de développement n'est jamais enregistré ni utilisé ici. Le rôle {@code SUPERADMIN} de la base
 * partagée est sauvegardé puis rétabli.
 */
class AmorcageDeDeveloppementIdnLectureCharacterizationIntegrationTest extends IdnLectureSupport {

    private static final String SUPERADMIN = "superadmin";

    private List<Map<String, Object>> roles, liensDeRole, comptes, liensDeCentre, liensDeSociete;
    private UUID alpha, beta, centreActif, centreFerme, centreBeta;

    @Override
    protected String dossier() {
        return "bootstrap";
    }

    @BeforeEach
    void monde() {
        alpha = societeNommee("SOCIETE_ALPHA", "ALPHA", "Alpha Sante", true);
        beta = societeNommee("SOCIETE_BETA", "BETA", "Beta Clinique", false);
        centreActif = centreNomme("CENTRE_ACTIF", alpha, "CA", "Alpha actif", true);
        centreFerme = centreNomme("CENTRE_FERME", alpha, "CF", "Alpha ferme", false);
        centreBeta = centreNomme("CENTRE_BETA", beta, "CB", "Beta d'une societe inactive", true);
        // Le compte et le role SUPERADMIN preexistants de la base partagee (autres tests, jeu de demo) portent les memes identifiants
        // constants que ceux de l'amorcage : ils sont SAUVEGARDES avant tout retrait, puis rendus a la fin du test.
        roles = jdbc.queryForList("SELECT id, code, name, description FROM app_role WHERE code = 'SUPERADMIN'");
        liensDeRole = jdbc.queryForList("SELECT user_id, role_id FROM app_user_role WHERE role_id IN (SELECT id FROM app_role WHERE code = 'SUPERADMIN')");
        comptes = jdbc.queryForList("SELECT id, username, password_hash, email, full_name, active FROM app_user WHERE username = 'superadmin'");
        liensDeCentre = jdbc.queryForList("SELECT user_id, center_id FROM app_user_center WHERE user_id IN (SELECT id FROM app_user WHERE username = 'superadmin')");
        liensDeSociete = jdbc.queryForList("SELECT user_id, societe_id FROM app_user_societe WHERE user_id IN (SELECT id FROM app_user WHERE username = 'superadmin')");
        retirerLeCompteEtLeRoleDeDeveloppement();
    }

    @AfterEach
    void rendreLaBase() {
        retirerLeCompteEtLeRoleDeDeveloppement();
        for (Map<String, Object> r : roles) {
            jdbc.update("INSERT INTO app_role (id, code, name, description) VALUES (?,?,?,?)", r.get("ID"), r.get("CODE"), r.get("NAME"), r.get("DESCRIPTION"));
        }
        for (Map<String, Object> c : comptes) {
            jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,?)",
                    c.get("ID"), c.get("USERNAME"), c.get("PASSWORD_HASH"), c.get("EMAIL"), c.get("FULL_NAME"), c.get("ACTIVE"));
        }
        for (Map<String, Object> l : liensDeRole) {
            jdbc.update("INSERT INTO app_user_role (user_id, role_id) SELECT ?, ? WHERE EXISTS (SELECT 1 FROM app_user WHERE id = ?)",
                    l.get("USER_ID"), l.get("ROLE_ID"), l.get("USER_ID"));
        }
        for (Map<String, Object> l : liensDeCentre) {
            jdbc.update("INSERT INTO app_user_center (user_id, center_id) SELECT ?, id FROM centers WHERE id = ?", l.get("USER_ID"), l.get("CENTER_ID"));
        }
        for (Map<String, Object> l : liensDeSociete) {
            jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) SELECT ?, id FROM societes WHERE id = ?", l.get("USER_ID"), l.get("SOCIETE_ID"));
        }
    }

    /** Retire le compte et le role de developpement (ceux du test ; les preexistants sont sauvegardes avant et rendus apres). */
    private void retirerLeCompteEtLeRoleDeDeveloppement() {
        String compte = "(SELECT id FROM app_user WHERE username = '" + SUPERADMIN + "')";
        jdbc.update("DELETE FROM auth_refresh_token WHERE user_id IN " + compte);
        jdbc.update("DELETE FROM app_user_center WHERE user_id IN " + compte);
        jdbc.update("DELETE FROM app_user_societe WHERE user_id IN " + compte);
        jdbc.update("DELETE FROM app_user_role WHERE user_id IN " + compte);
        jdbc.update("DELETE FROM app_user WHERE username = '" + SUPERADMIN + "'");
        jdbc.update("DELETE FROM app_user_role WHERE role_id IN (SELECT id FROM app_role WHERE code = 'SUPERADMIN')");
        jdbc.update("DELETE FROM app_role WHERE code = 'SUPERADMIN'");
    }

    private DevSuperAdminBootstrapInitializer amorceur(boolean active, boolean clesEphemeres) {
        LicenseKeyProperties cles = mock(LicenseKeyProperties.class);
        when(cles.isEphemeralDevMode()).thenReturn(clesEphemeres);
        return new DevSuperAdminBootstrapInitializer(active, cles, jdbc, encodeur);
    }

    private void etat(String libelle) {
        traceSql(libelle + " : role", "SELECT code, name, description FROM app_role WHERE code = 'SUPERADMIN'");
        traceSql(libelle + " : compte", "SELECT username, email, full_name, active, (password_hash IS NOT NULL AND password_hash <> '') AS mot_de_passe_pose "
                + "FROM app_user WHERE username = 'superadmin'");
        traceSql(libelle + " : roles du compte", "SELECT r.code FROM app_user_role ur JOIN app_role r ON r.id = ur.role_id JOIN app_user u ON u.id = ur.user_id "
                + "WHERE u.username = 'superadmin'");
        traceSql(libelle + " : centres de la fixture rattaches", "SELECT uc.center_id FROM app_user_center uc JOIN app_user u ON u.id = uc.user_id "
                + "WHERE u.username = 'superadmin' AND uc.center_id IN (?, ?, ?) ORDER BY uc.center_id", centreActif, centreFerme, centreBeta);
    }

    /** Après un amorçage actif : AUCUN centre de la base (fixtures ou autres) n'échappe au compte. */
    private void tousLesCentresDeLaBase(String libelle) {
        traceSql(libelle + " : centres de la base sans rattachement", "SELECT COUNT(*) AS nombre FROM centers c WHERE NOT EXISTS "
                + "(SELECT 1 FROM app_user_center uc JOIN app_user u ON u.id = uc.user_id WHERE u.username = 'superadmin' AND uc.center_id = c.id)");
    }

    @Test
    void sans_activation_ou_avec_de_vraies_cles_rien_n_est_cree() {
        amorceur(false, true).run();
        etat("desactive par defaut");
        amorceur(true, false).run();
        etat("active mais cles de licence reelles");
        verrouiller("sans_effet");
    }

    @Test
    void l_amorcage_cree_le_role_le_compte_et_rattache_tous_les_centres() {
        amorceur(true, true).run();
        etat("premier demarrage");
        tousLesCentresDeLaBase("premier demarrage");
        verrouiller("premier_demarrage");
    }

    @Test
    void l_amorcage_est_idempotent_et_rattrape_les_centres_crees_entre_deux_demarrages() {
        amorceur(true, true).run();
        amorceur(true, true).run();
        etat("deuxieme demarrage");
        tousLesCentresDeLaBase("deuxieme demarrage");
        UUID nouveau = centreNomme("CENTRE_NOUVEAU", alpha, "CN", "Alpha nouveau", true);
        amorceur(true, true).run();
        traceSql("troisieme demarrage : le centre cree entre-temps est rattache", "SELECT COUNT(*) AS nombre FROM app_user_center uc JOIN app_user u ON u.id = uc.user_id "
                + "WHERE u.username = 'superadmin' AND uc.center_id = ?", nouveau);
        traceSql("troisieme demarrage : un seul compte, un seul role", "SELECT (SELECT COUNT(*) FROM app_user WHERE username = 'superadmin') AS comptes, "
                + "(SELECT COUNT(*) FROM app_role WHERE code = 'SUPERADMIN') AS roles");
        verrouiller("rejeu");
    }

    @Test
    void un_compte_superadmin_existant_n_est_ni_modifie_ni_reactive() {
        UUID existant = id("COMPTE_EXISTANT");
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,FALSE)",
                existant, SUPERADMIN, encodeur.encode("Mdp-Fictif-Idn-2026"), "ancien@example.test", "Compte existant");
        jdbc.update("INSERT INTO app_role (id, code, name, description) VALUES (?,?,?,?)", UUID.randomUUID(), "SUPERADMIN", "Role existant", "deja la");
        amorceur(true, true).run();
        etat("compte et role preexistants");
        tousLesCentresDeLaBase("compte et role preexistants");
        verrouiller("compte_existant");
    }
}
