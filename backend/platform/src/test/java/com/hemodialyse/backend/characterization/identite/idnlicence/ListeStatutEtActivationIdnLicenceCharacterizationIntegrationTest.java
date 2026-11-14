package com.hemodialyse.backend.characterization.identite.idnlicence;

import com.hemodialyse.backend.application.license.LicenseService;
import com.hemodialyse.backend.infrastructure.scheduling.LicenseOnlineCheckJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

/**
 * IDN-licence, lecture et vie d'une licence : liste (centre et société de chaque licence), statut du centre de la session, activation
 * d'une clé générée ailleurs, révocation, autorité de vérification, vérification en ligne sans réseau. Comportement ACTUEL.
 */
class ListeStatutEtActivationIdnLicenceCharacterizationIntegrationTest extends IdnLicenceSupport {

    private static final String L = "/api/v1/licenses";

    @Autowired
    private LicenseOnlineCheckJob verificationEnLigne;
    @Autowired
    private LicenseService serviceDeLicence;

    private UUID alpha, beta, delta;
    private UUID alpha1, alpha2, betaNord, delta1, delta2, delta3, delta4;
    private UUID administrateur;

    @Override
    protected String dossier() {
        return "licence";
    }

    @BeforeEach
    void monde() {
        alpha = societeNommee("SOCIETE_ALPHA", "ALPHA", "Alpha Sante", true);
        beta = societeNommee("SOCIETE_BETA_INACTIVE", "BETA", "Beta Clinique", false);
        delta = societeNommee("SOCIETE_DELTA", "DELTA", "Delta Dialyse", true);
        alpha1 = centreNomme("CENTRE_ALPHA_1", alpha, "A1", "Alpha 1", true);
        alpha2 = centreNomme("CENTRE_ALPHA_2", alpha, "A2", "Alpha 2", true);
        betaNord = centreNomme("CENTRE_BETA_NORD", beta, "BN", "Beta Nord", true);
        delta1 = centreNomme("CENTRE_DELTA_1", delta, "D1", "Delta 1", true);
        delta2 = centreNomme("CENTRE_DELTA_2", delta, "D2", "Delta 2", true);
        delta3 = centreNomme("CENTRE_DELTA_3", delta, "D3", "Delta 3", true);
        delta4 = centreNomme("CENTRE_DELTA_4", delta, "D4", "Delta 4", false);
        administrateur = compte("COMPTE_ADMIN", "admin", true, "ADMIN");
        rattacher(administrateur, alpha1);
        supprimerLesLicencesDeLaFixture();
    }

    private RequestPostProcessor admin(UUID centre) {
        return session(administrateur, centre, null, "ADMIN");
    }

    @Test
    void la_liste_des_licences_porte_le_nom_du_centre_et_de_sa_societe() throws Exception {
        UUID centreSupprime = UUID.nameUUIDFromBytes("centre-supprime".getBytes());
        declarerCentreSansLigne(centreSupprime, "CENTRE_SUPPRIME");
        licence("LICENCE_ALPHA_1", alpha1, "ACTIVE", "cle-fictive-1", "jti-fictif-1", 3, "2026-01-03T10:00:00Z");
        licence("LICENCE_ALPHA_2", alpha2, "ACTIVE", "cle-fictive-2", "jti-fictif-2", 5, null);
        licence("LICENCE_BETA", betaNord, "ACTIVE", "cle-fictive-3", "jti-fictif-3", 4, null);
        licence("LICENCE_ORPHELINE", centreSupprime, "ACTIVE", "cle-fictive-4", "jti-fictif-4", 6, null);
        licence("LICENCE_REVOQUEE", alpha1, "REVOKED", "cle-fictive-5", "jti-fictif-5", 2, null);
        tracer("liste (plus recente d'abord ; societe inactive et centre supprime inclus)", doGet(L, superadmin()), licencesDeLaFixture(), null);
        jdbc.update("UPDATE centers SET name = ? WHERE id = ?", "Alpha 1 renomme", alpha1);
        jdbc.update("UPDATE societes SET raison_sociale = ? WHERE id = ?", "Alpha Sante renomme", alpha);
        tracer("liste apres renommage du centre et de la societe", doGet(L, superadmin()), licencesDeLaFixture(), null);
        tracer("ADMIN", doGet(L, admin(alpha1)));
        tracer("DIRECTION", doGet(L, session(administrateur, null, alpha, "DIRECTION")));
        tracer("sans session", doGet(L, null));
        verrouiller("liste");
    }

    @Test
    void le_statut_du_centre_de_la_session() throws Exception {
        tracer("SUPERADMIN : sans objet", doGet(L + "/status", superadmin()));
        tracer("DIRECTION : refusee par le perimetre de role",doGet(L + "/status", session(administrateur, null, alpha, "DIRECTION")));
        tracer("ADMIN sans centre : sans objet", doGet(L + "/status", session(administrateur, null, null, "ADMIN")));
        tracer("centre sans licence", doGet(L + "/status", admin(alpha1)));
        tracer("sans session", doGet(L + "/status", null));

        tracer("emission pour Alpha 1", doPost(L + "/issue", emission(alpha1, "STANDARD", 5), superadmin()));
        tracer("centre licencie, aucun poste occupe hors l'administrateur", doGet(L + "/status", admin(alpha1)));
        UUID marie = compte("COMPTE_MARIE", "marie", true, "INFIRMIER");
        UUID paul = compte("COMPTE_PAUL", "paul", false, "MEDECIN");
        UUID inconnu = compte("COMPTE_AUTRE_CENTRE", "autre", true, "INFIRMIER");
        rattacher(marie, alpha1);
        rattacher(paul, alpha1);
        rattacher(inconnu, alpha2);
        tracer("postes occupes : seuls les comptes actifs du centre comptent", doGet(L + "/status", admin(alpha1)));

        tracer("emission pour Alpha 2 puis revocation avec motif", doPost(L + "/issue", emission(alpha2, "ESSAI", 2), superadmin()));
        UUID licenceAlpha2 = jdbc.queryForObject("SELECT id FROM license WHERE center_id = ?", UUID.class, alpha2);
        aliasNommes.put(licenceAlpha2.toString(), "<LICENCE_ALPHA_2>");
        tracer("revocation avec motif", doPost(L + "/" + licenceAlpha2 + "/revoke", "{'reason':'Impaye'}", superadmin()));
        tracer("statut d'un centre dont la licence est revoquee", doGet(L + "/status", admin(alpha2)));
        tracer("revocation d'une licence inconnue", doPost(L + "/" + UUID.nameUUIDFromBytes("inconnue".getBytes()) + "/revoke",
                "{'reason':'x'}", superadmin()));
        tracer("revocation par ADMIN", doPost(L + "/" + licenceAlpha2 + "/revoke", "{'reason':'x'}", admin(alpha1)));

        licence("LICENCE_REVOQUEE_SANS_MOTIF", betaNord, "REVOKED", "cle-fictive", "jti-fictif-revoquee", 4, null);
        tracer("statut : licence revoquee sans motif", doGet(L + "/status", admin(betaNord)));
        licence("LICENCE_AUTRE_CENTRE", delta1, "ACTIVE", cle(alpha1, CODE_PREFIXE + "A1", "STANDARD", 5), "jti-fictif-autre", 4, null);
        tracer("statut : la cle stockee appartient a un autre centre (alteration)", doGet(L + "/status", admin(delta1)));
        licence("LICENCE_ILLISIBLE", delta2, "ACTIVE", "pas-un-jeton", "jti-fictif-illisible", 4, null);
        tracer("statut : cle stockee illisible", doGet(L + "/status", admin(delta2)));
        licence("LICENCE_EXPIREE", delta3, "ACTIVE", cleExpiree(delta3, CODE_PREFIXE + "D3"), "jti-fictif-expiree", 4, null);
        tracer("statut : cle expiree", doGet(L + "/status", admin(delta3)));
        licence("LICENCE_ANCIENNE", delta4, "ACTIVE", cle(delta4, CODE_PREFIXE + "D4", "STANDARD", 5), "jti-fictif-ancienne", 1, null);
        licence("LICENCE_RECENTE_REVOQUEE", delta4, "REVOKED", "cle-fictive", "jti-fictif-recente", 9, null);
        tracer("statut : seule la licence la plus recente compte", doGet(L + "/status", admin(delta4)));
        verrouiller("statut");
    }

    @Test
    void l_activation_d_une_cle_generee_ailleurs() throws Exception {
        tracer("ADMIN active la cle de son centre", doPost(L + "/activate",
                "{'centerId':null,'licenseKey':'" + cle(alpha1, CODE_PREFIXE + "A1", "STANDARD", 5) + "'}", admin(alpha1)));
        String cleDejaActivee = jdbc.queryForObject("SELECT license_key FROM license WHERE center_id = ?", String.class, alpha1);
        tracer("la meme cle une seconde fois", doPost(L + "/activate", "{'licenseKey':'" + cleDejaActivee + "'}", admin(alpha1)));
        tracer("ADMIN : la cle d'un autre centre est refusee (le centre du corps est ignore)", doPost(L + "/activate",
                "{'centerId':'" + alpha2 + "','licenseKey':'" + cle(alpha2, CODE_PREFIXE + "A2", "STANDARD", 5) + "'}", admin(alpha1)));
        tracer("ADMIN : le centre du corps est ignore, la cle de son centre est activee", doPost(L + "/activate",
                "{'centerId':'" + alpha2 + "','licenseKey':'" + cle(alpha1, CODE_PREFIXE + "A1", "ESSAI", 3) + "'}", admin(alpha1)));
        tracer("SUPERADMIN active pour le centre du corps", doPost(L + "/activate",
                "{'centerId':'" + alpha2 + "','licenseKey':'" + cle(alpha2, CODE_PREFIXE + "A2", "STANDARD", 8) + "'}", superadmin()));
        tracer("SUPERADMIN : cle d'un autre centre", doPost(L + "/activate",
                "{'centerId':'" + alpha2 + "','licenseKey':'" + cle(alpha1, CODE_PREFIXE + "A1", "STANDARD", 5) + "'}", superadmin()));
        UUID inconnu = UUID.nameUUIDFromBytes("centre-inconnu".getBytes());
        declarerCentreSansLigne(inconnu, "CENTRE_INCONNU");
        tracer("SUPERADMIN : centre inconnu, cle valide pour ce centre : acceptee", doPost(L + "/activate",
                "{'centerId':'" + inconnu + "','licenseKey':'" + cle(inconnu, "INCONNU", "STANDARD", 1) + "'}", superadmin()));
        tracer("cle illisible", doPost(L + "/activate", "{'licenseKey':'pas-un-jeton'}", admin(alpha1)));
        tracer("cle expiree", doPost(L + "/activate", "{'licenseKey':'" + cleExpiree(alpha1, CODE_PREFIXE + "A1") + "'}", admin(alpha1)));
        tracer("cle vide", doPost(L + "/activate", "{'licenseKey':''}", admin(alpha1)));
        tracer("cle absente", doPost(L + "/activate", "{}", admin(alpha1)));
        tracer("DIRECTION", doPost(L + "/activate", "{'licenseKey':'x'}", session(administrateur, null, alpha, "DIRECTION")));
        tracer("MEDECIN", doPost(L + "/activate", "{'licenseKey':'x'}", session(administrateur, alpha1, null, "MEDECIN")));
        tracer("sans session", doPost(L + "/activate", "{'licenseKey':'x'}", null));
        tracer("ADMIN sans centre", doPost(L + "/activate", "{'licenseKey':'" + cle(alpha1, CODE_PREFIXE + "A1", "STANDARD", 5) + "'}",
                session(administrateur, null, null, "ADMIN")));
        traceSql("licences en base", "SELECT center_id, max_users, status, type FROM license WHERE center_id IN (?,?,?) "
                + "ORDER BY center_id, max_users, type", alpha1, alpha2, inconnu);
        verrouiller("activation");
    }

    @Test
    void la_verification_en_ligne_sans_reseau_et_l_autorite() throws Exception {
        licence("LICENCE_ACTIVE", alpha1, "ACTIVE", cle(alpha1, CODE_PREFIXE + "A1", "STANDARD", 5), "jti-fictif-actif", 3, "2026-01-03T10:00:00Z");
        licence("LICENCE_REVOQUEE", alpha2, "REVOKED", "cle-fictive", "jti-fictif-revoque", 3, null);
        verificationEnLigne.checkAll();
        traceSql("apres la verification en ligne (instance autorite, aucune URL, aucun reseau) : rien n'est enregistre",
                "SELECT center_id, status, last_online_check_at IS NOT NULL AS verification_enregistree FROM license WHERE center_id IN (?,?) "
                        + "ORDER BY center_id", alpha1, alpha2);
        tracer("autorite sans session : refusee (la route n'est pas publique en pratique)",
                doGet(L + "/authority/verify?jti=jti-fictif-actif", null));
        tracer("autorite : licence active", doGet(L + "/authority/verify?jti=jti-fictif-actif", superadmin()));
        tracer("autorite : licence revoquee", doGet(L + "/authority/verify?jti=jti-fictif-revoque", superadmin()));
        tracer("autorite : jti inconnu", doGet(L + "/authority/verify?jti=jti-inconnu", superadmin()));
        tracer("autorite : jti absent", doGet(L + "/authority/verify", superadmin()));
        UUID licenceActive = jdbc.queryForObject("SELECT id FROM license WHERE center_id = ?", UUID.class, alpha1);
        serviceDeLicence.recordOnlineCheck(licenceActive);
        serviceDeLicence.recordOnlineCheck(UUID.nameUUIDFromBytes("inconnue".getBytes()));
        traceSql("apres un enregistrement de verification (la licence inconnue est ignoree)",
                "SELECT center_id, last_online_check_at IS NOT NULL AS verification_enregistree FROM license WHERE center_id IN (?,?) "
                        + "ORDER BY center_id", alpha1, alpha2);
        tracer("statut apres verification", doGet(L + "/status", admin(alpha1)));
        verrouiller("verification_en_ligne");
    }
}
