package com.hemodialyse.backend.characterization.identite.idnlicence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * IDN-licence, émission : {@code POST /api/v1/licenses/issue} (un centre) et {@code /issue-societe} (les centres actifs d'une société, ou
 * une sélection, en une transaction). Lecture du code du centre, de son existence, de son état et de ses voisins de société. Comportement
 * ACTUEL ; les listes sont filtrées sur les centres de ce filet (la base de test est partagée).
 */
class EmissionDesLicencesIdnLicenceCharacterizationIntegrationTest extends IdnLicenceSupport {

    private static final String L = "/api/v1/licenses";

    private UUID alpha, beta, gamma, vide;
    private UUID alpha1, alpha2, alphaFerme, betaNord, gammaFerme;
    private UUID administrateur;

    @Override
    protected String dossier() {
        return "emission";
    }

    @BeforeEach
    void monde() {
        alpha = societeNommee("SOCIETE_ALPHA", "ALPHA", "Alpha Sante", true);
        beta = societeNommee("SOCIETE_BETA_INACTIVE", "BETA", "Beta Clinique", false);
        gamma = societeNommee("SOCIETE_GAMMA", "GAMMA", "Gamma Dialyse", true);
        vide = societeNommee("SOCIETE_VIDE", "VIDE", "Vide Sans Centre", true);
        // Noms volontairement dans l'ordre inverse des codes : l'ordre des licences émises suit le NOM du centre.
        alpha2 = centreNomme("CENTRE_ALPHA_2", alpha, "A2", "Alpha 1 bis", true);
        alpha1 = centreNomme("CENTRE_ALPHA_1", alpha, "A1", "Alpha 2 ter", true);
        alphaFerme = centreNomme("CENTRE_ALPHA_FERME", alpha, "AF", "Alpha ferme", false);
        betaNord = centreNomme("CENTRE_BETA_NORD", beta, "BN", "Beta Nord", true);
        gammaFerme = centreNomme("CENTRE_GAMMA_FERME", gamma, "GF", "Gamma ferme", false);
        administrateur = compte("COMPTE_ADMIN", "admin", true, "ADMIN");
        rattacher(administrateur, alpha1);
        supprimerLesLicencesDeLaFixture();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return session(administrateur, alpha1, null, "ADMIN");
    }

    @Test
    void l_emission_pour_un_centre_lit_son_code_et_son_existence() throws Exception {
        tracer("emission pour Alpha 1 (SUPERADMIN)", doPost(L + "/issue", emission(alpha1, "STANDARD", 5), superadmin()));
        tracer("emission pour un centre desactive : acceptee", doPost(L + "/issue", emission(alphaFerme, "ESSAI", 2), superadmin()));
        tracer("emission pour le centre d'une societe desactivee : acceptee", doPost(L + "/issue", emission(betaNord, "STANDARD", 9), superadmin()));
        tracer("emission pour un centre inconnu", doPost(L + "/issue", emission(UUID.nameUUIDFromBytes("inconnu".getBytes()), "STANDARD", 5),
                superadmin()));
        tracer("emission : le code du centre est un champ du jeton",
                doPost(L + "/issue", emission(alpha2, "STANDARD", 1), superadmin()));
        traceSql("licences en base apres les emissions", "SELECT center_id, max_users, status, type FROM license WHERE center_id IN (?,?,?,?) "
                + "ORDER BY center_id", alpha1, alphaFerme, betaNord, alpha2);
        String jeton = jdbc.queryForObject("SELECT license_key FROM license WHERE center_id = ?", String.class, alpha1);
        traceNote("le code du centre figure dans le jeton signe",
                String.valueOf(jetons.verify(jeton).centerCode().equals(CODE_PREFIXE + "A1")));
        traceNote("l'identifiant du centre figure dans le jeton", String.valueOf(jetons.verify(jeton).centerId().equals(alpha1)));
        verrouiller("par_centre");
    }

    @Test
    void les_acces_a_l_emission_par_centre() throws Exception {
        tracer("ADMIN", doPost(L + "/issue", emission(alpha1, "STANDARD", 5), admin()));
        tracer("DIRECTION", doPost(L + "/issue", emission(alpha1, "STANDARD", 5), session(administrateur, null, alpha, "DIRECTION")));
        tracer("MEDECIN", doPost(L + "/issue", emission(alpha1, "STANDARD", 5), session(administrateur, alpha1, null, "MEDECIN")));
        tracer("sans session", doPost(L + "/issue", emission(alpha1, "STANDARD", 5), null));
        tracer("ADMIN avec un corps a dates illisibles",
                doPost(L + "/issue", "{'centerId':'" + alpha1 + "','type':'STANDARD','maxUsers':5,'validFrom':'pas-une-date','validUntil':'x'}", admin()));
        tracer("SUPERADMIN avec un corps a dates illisibles",
                doPost(L + "/issue", "{'centerId':'" + alpha1 + "','type':'STANDARD','maxUsers':5,'validFrom':'pas-une-date','validUntil':'x'}",
                        superadmin()));
        traceSql("aucune licence emise", "SELECT center_id FROM license WHERE center_id = ?", alpha1);
        verrouiller("acces_par_centre");
    }

    @Test
    void l_emission_pour_une_societe_lit_ses_centres_actifs_par_nom() throws Exception {
        tracer("tous les centres actifs d'Alpha, par nom", doPost(L + "/issue-societe", emissionSociete(alpha, null, "STANDARD", 5), superadmin()));
        tracer("selection d'un centre actif seulement", doPost(L + "/issue-societe", emissionSociete(alpha, List.of(alpha2), "ESSAI", 3),
                superadmin()));
        tracer("selection avec un centre desactive : refus", doPost(L + "/issue-societe",
                emissionSociete(alpha, List.of(alpha1, alphaFerme), "STANDARD", 5), superadmin()));
        tracer("selection avec le centre d'une autre societe : refus", doPost(L + "/issue-societe",
                emissionSociete(alpha, List.of(alpha1, betaNord), "STANDARD", 5), superadmin()));
        tracer("selection avec un centre inconnu : refus", doPost(L + "/issue-societe",
                emissionSociete(alpha, List.of(alpha1, UUID.nameUUIDFromBytes("inconnu".getBytes())), "STANDARD", 5), superadmin()));
        tracer("selection vide : tous les centres actifs", doPost(L + "/issue-societe", emissionSociete(alpha, List.of(), "STANDARD", 7),
                superadmin()));
        tracer("selection avec un doublon : une seule licence", doPost(L + "/issue-societe",
                emissionSociete(alpha, List.of(alpha1, alpha1), "STANDARD", 4), superadmin()));
        traceSql("licences d'Alpha en base", "SELECT center_id, max_users, status, type FROM license WHERE center_id IN (?,?,?) "
                + "ORDER BY center_id, max_users", alpha1, alpha2, alphaFerme);
        verrouiller("par_societe");
    }

    @Test
    void les_refus_de_l_emission_pour_une_societe() throws Exception {
        tracer("societe desactivee dont le centre est actif : acceptee", doPost(L + "/issue-societe", emissionSociete(beta, null, "STANDARD", 5),
                superadmin()));
        tracer("societe dont le seul centre est desactive", doPost(L + "/issue-societe", emissionSociete(gamma, null, "STANDARD", 5), superadmin()));
        tracer("societe sans centre", doPost(L + "/issue-societe", emissionSociete(vide, null, "STANDARD", 5), superadmin()));
        tracer("societe inconnue", doPost(L + "/issue-societe",
                emissionSociete(UUID.nameUUIDFromBytes("societe-inconnue".getBytes()), null, "STANDARD", 5), superadmin()));
        tracer("societe absente", doPost(L + "/issue-societe", emissionSociete(null, null, "STANDARD", 5), superadmin()));
        tracer("dates inversees", doPost(L + "/issue-societe", "{'societeId':'" + alpha + "','type':'STANDARD','maxUsers':5,'validFrom':'" + FIN
                + "','validUntil':'" + DEBUT + "'}", superadmin()));
        tracer("zero poste", doPost(L + "/issue-societe", emissionSociete(alpha, null, "STANDARD", 0), superadmin()));
        tracer("ADMIN", doPost(L + "/issue-societe", emissionSociete(alpha, null, "STANDARD", 5), admin()));
        tracer("DIRECTION", doPost(L + "/issue-societe", emissionSociete(alpha, null, "STANDARD", 5),
                session(administrateur, null, alpha, "DIRECTION")));
        tracer("ADMIN avec un corps a dates illisibles", doPost(L + "/issue-societe",
                "{'societeId':'" + alpha + "','type':'STANDARD','maxUsers':5,'validFrom':'pas-une-date','validUntil':'x'}", admin()));
        traceSql("licences en base", "SELECT center_id FROM license WHERE center_id IN (?,?,?,?) ORDER BY center_id", alpha1, betaNord, gammaFerme,
                alpha2);
        verrouiller("refus_par_societe");
    }
}
