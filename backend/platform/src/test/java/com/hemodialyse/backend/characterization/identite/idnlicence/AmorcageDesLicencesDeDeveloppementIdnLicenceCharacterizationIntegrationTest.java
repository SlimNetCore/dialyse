package com.hemodialyse.backend.characterization.identite.idnlicence;

import com.hemodialyse.backend.infrastructure.config.DevLicenseBootstrapInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

/**
 * IDN-licence, amorçage de développement ({@link DevLicenseBootstrapInitializer}, clé éphémère) : une licence par centre qui n'en a
 * pas de valide, tous les centres lus (actifs ou non, société active ou non). Comportement ACTUEL ; seules les lignes de la fixture sont
 * enregistrées (la base de test est partagée : les autres centres sont servis eux aussi, sans effet sur ce filet).
 */
class AmorcageDesLicencesDeDeveloppementIdnLicenceCharacterizationIntegrationTest extends IdnLicenceSupport {

    @Autowired
    private DevLicenseBootstrapInitializer amorcage;

    private UUID alpha1, alphaFerme, betaNord, gamma1, gamma2;

    @Override
    protected String dossier() {
        return "amorcage";
    }

    @BeforeEach
    void monde() {
        UUID alpha = societeNommee("SOCIETE_ALPHA", "ALPHA", "Alpha Sante", true);
        UUID beta = societeNommee("SOCIETE_BETA_INACTIVE", "BETA", "Beta Clinique", false);
        UUID gamma = societeNommee("SOCIETE_GAMMA", "GAMMA", "Gamma Dialyse", true);
        alpha1 = centreNomme("CENTRE_ALPHA_1", alpha, "A1", "Alpha 1", true);
        alphaFerme = centreNomme("CENTRE_ALPHA_FERME", alpha, "AF", "Alpha ferme", false);
        betaNord = centreNomme("CENTRE_BETA_NORD", beta, "BN", "Beta Nord", true);
        gamma1 = centreNomme("CENTRE_GAMMA_1", gamma, "G1", "Gamma 1", true);
        gamma2 = centreNomme("CENTRE_GAMMA_2", gamma, "G2", "Gamma 2", true);
        supprimerLesLicencesDeLaFixture();
    }

    @Test
    void une_licence_de_developpement_pour_chaque_centre_sans_licence_valide() {
        // Gamma 1 : licence révoquée volontairement (conservée). Gamma 2 : clé illisible (ré-émise). Beta Nord : clé valide (conservée).
        licence("LICENCE_REVOQUEE", gamma1, "REVOKED", "cle-fictive", "jti-fictif-revoque", 3, null);
        licence("LICENCE_ILLISIBLE", gamma2, "ACTIVE", "pas-un-jeton", "jti-fictif-illisible", 3, null);
        licence("LICENCE_VALIDE", betaNord, "ACTIVE", cle(betaNord, CODE_PREFIXE + "BN", "STANDARD", 5), "jti-fictif-valide", 3, null);
        amorcage.run();
        traceSql("apres le premier amorcage (une licence de 50 postes, STANDARD, par centre non servi)",
                "SELECT center_id, max_users, status, type FROM license WHERE center_id IN (?,?,?,?,?) "
                        + "ORDER BY center_id, status, max_users", alpha1, alphaFerme, betaNord, gamma1, gamma2);
        amorcage.run();
        traceSql("apres le rejeu : aucune nouvelle licence",
                "SELECT center_id, max_users, status, type FROM license WHERE center_id IN (?,?,?,?,?) "
                        + "ORDER BY center_id, status, max_users", alpha1, alphaFerme, betaNord, gamma1, gamma2);
        UUID nouvelle = jdbc.queryForObject("SELECT id FROM license WHERE center_id = ?", UUID.class, alpha1);
        String jeton = jdbc.queryForObject("SELECT license_key FROM license WHERE center_id = ?", String.class, alpha1);
        traceNote("le jeton emis porte le code du centre", String.valueOf(jetons.verify(jeton).centerCode().equals(CODE_PREFIXE + "A1")));
        traceNote("la licence emise n'a pas de createur", String.valueOf(jdbc.queryForObject(
                "SELECT created_by IS NULL FROM license WHERE id = ?", Boolean.class, nouvelle)));
        verrouiller("developpement");
    }
}
