package com.hemodialyse.backend.characterization.dossiermedical.dm2;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM2, lot 2 : {@code GET /patients/{id}/alertes-observance} et {@code PUT .../alertes-observance/{id}/resoudre} (acquittement par le corps
 * médical). Les alertes sont posées en base (le planificateur qui les ouvre est caractérisé à part). Données fictives.
 */
class LesAlertesDObservanceCharacterizationIntegrationTest extends PrescriptionsCharacterizationSupport {

    @Override
    protected String dossier() {
        return "alertes";
    }

    private UUID patientA;
    private UUID patientB;

    private void decorAlertes() {
        patientA = patientDm2(CENTRE_A, "PAT_A");
        patientB = patientDm2(CENTRE_B, "PAT_B");
    }

    private String alertes(UUID patient) {
        return PATIENTS_API + "/" + patient + "/alertes-observance";
    }

    private String resoudre(UUID patient, UUID alerte) {
        return alertes(patient) + "/" + alerte + "/resoudre";
    }

    @Test
    void les_alertes_sont_listees_de_la_plus_recente_a_la_plus_ancienne_resolues_comprises() throws Exception {
        decorAlertes();
        alerte(CENTRE_A, patientA, "ANCIENNE", "EPO", "RETARD_CONSTATE", fixe(1), fixe(7), 8000, 4000, "Retard constate sur le traitement EPO.", 1, true);
        alerte(CENTRE_A, patientA, "MILIEU", "FER_INJECTABLE", "RAPPEL_ECHEANCE", fixe(8), fixe(14), 200, 100, "Il reste a administrer 100 mg de fer.", 2, false);
        alerte(CENTRE_A, patientA, "RECENTE", "EPO", "RAPPEL_ECHEANCE", fixe(15), fixe(21), 8000, 0, "Il reste a administrer 8000 UI de EPO.", 3, false);
        alerte(CENTRE_B, patientB, "AUTRE_CENTRE", "EPO", "RETARD_CONSTATE", fixe(1), fixe(7), 8000, 0, "Alerte du centre B.", 1, false);
        lecture("liste", doGet(alertes(patientA), as(CENTRE_A, "MEDECIN")));
    }

    @Test
    void une_alerte_resolue_garde_sa_date_de_resolution_et_le_rejeu_la_reecrit() throws Exception {
        decorAlertes();
        UUID a = alerte(CENTRE_A, patientA, "ALERTE", "EPO", "RETARD_CONSTATE", fixe(1), fixe(7), 8000, 4000, "Retard.", 1, false);
        Resp premiere = doPut(resoudre(patientA, a), "", as(CENTRE_A, "MEDECIN"));
        ecriture("premiere_resolution", premiere);
        String apres = jdbc.queryForObject("SELECT CAST(resolved_at AS VARCHAR) FROM alertes_observance WHERE id = ?", String.class, a);
        // Le rejeu d'une alerte deja resolue n'est pas refuse : la date de resolution est reecrite (pas d'idempotence stricte).
        jdbc.update("UPDATE alertes_observance SET resolved_at = TIMESTAMP WITH TIME ZONE '2026-01-02 00:00:00+00:00' WHERE id = ?", a);
        Resp rejeu = doPut(resoudre(patientA, a), "", as(CENTRE_A, "ADMIN"));
        ecriture("rejeu", rejeu);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resolved_at apres la premiere resolution est renseigne", apres != null);
        m.put("rejeu : resolved_at reecrit (different de la valeur posee)",
                !jdbc.queryForObject("SELECT CAST(resolved_at AS VARCHAR) FROM alertes_observance WHERE id = ?", String.class, a).startsWith("2026-01-02"));
        arbre("rejeu_etat", m);
        etatDesAlertes("etat");
    }

    @Test
    void les_erreurs_de_la_resolution() throws Exception {
        decorAlertes();
        UUID a = alerte(CENTRE_A, patientA, "ALERTE_A", "EPO", "RETARD_CONSTATE", fixe(1), fixe(7), 8000, 4000, "Retard.", 1, false);
        UUID b = alerte(CENTRE_B, patientB, "ALERTE_B", "EPO", "RETARD_CONSTATE", fixe(1), fixe(7), 8000, 4000, "Retard.", 1, false);
        UUID autrePatient = patientDm2(CENTRE_A, "PAT_A2");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("alerte inconnue", statutEtCode(doPut(resoudre(patientA, idStable("alerte", "INCONNUE")), "", as(CENTRE_A, "MEDECIN"))));
        cas.put("alerte d'un autre patient du meme centre", statutEtCode(doPut(resoudre(autrePatient, a), "", as(CENTRE_A, "MEDECIN"))));
        cas.put("alerte du centre B depuis le centre A", statutEtCode(doPut(resoudre(patientB, b), "", as(CENTRE_A, "MEDECIN"))));
        cas.put("centerId B demande (session A)", statutEtCode(doPut(resoudre(patientB, b) + "?centerId=" + CENTRE_B, "", as(CENTRE_A, "MEDECIN"))));
        cas.put("identifiant d'alerte illisible", statutEtCode(doPut(alertes(patientA) + "/pas-un-uuid/resoudre", "", as(CENTRE_A, "MEDECIN"))));
        cas.put("centerId illisible", statutEtCode(doPut(resoudre(patientA, a) + "?centerId=xx", "", as(CENTRE_A, "MEDECIN"))));
        arbre("erreurs", cas);
        etatDesAlertes("etat");
    }

    @Test
    void les_droits_par_profil_sur_la_liste_et_la_resolution() throws Exception {
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm2().entrySet()) {
            nettoyerDm2();
            decorAlertes();
            UUID a = alerte(CENTRE_A, patientA, "ALERTE", "EPO", "RETARD_CONSTATE", fixe(1), fixe(7), 8000, 4000, "Retard.", 1, false);
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("liste", statutEtCode(doGet(alertes(patientA), p.getValue())));
            ligne.put("resolution", statutEtCode(doPut(resoudre(patientA, a), "", p.getValue())));
            profils.put(p.getKey(), ligne);
        }
        nettoyerDm2();
        decorAlertes();
        UUID a = alerte(CENTRE_A, patientA, "ALERTE", "EPO", "RETARD_CONSTATE", fixe(1), fixe(7), 8000, 4000, "Retard.", 1, false);
        Map<String, Object> anonyme = new LinkedHashMap<>();
        anonyme.put("liste", statutEtCode(doGetAnonymous(alertes(patientA))));
        anonyme.put("resolution", statutEtCode(appel("PUT", resoudre(patientA, a), "", null)));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
    }

    @Test
    void le_cloisonnement_et_les_effets_de_la_resolution_sur_le_journal_les_notifications_et_les_diffusions() throws Exception {
        decorAlertes();
        UUID a = alerte(CENTRE_A, patientA, "ALERTE_A", "EPO", "RETARD_CONSTATE", fixe(1), fixe(7), 8000, 4000, "Retard.", 1, false);
        alerte(CENTRE_B, patientB, "ALERTE_B", "EPO", "RETARD_CONSTATE", fixe(1), fixe(7), 8000, 4000, "Retard.", 1, false);
        monterLAudit();
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("liste du patient B par le centre A", listeLisible(doGet(alertes(patientB), as(CENTRE_A, "MEDECIN"))));
        cas.put("liste avec centerId B (session A)", statutEtCode(doGet(alertes(patientB) + "?centerId=" + CENTRE_B, as(CENTRE_A, "MEDECIN"))));
        cas.put("liste du patient A par le centre B", listeLisible(doGet(alertes(patientA), as(CENTRE_B, "MEDECIN"))));
        cas.put("MEDECIN sans centre de session", statutEtCode(doGet(alertes(patientA), as(null, "MEDECIN"))));
        assertThat(doPut(resoudre(patientA, a), "", as(CENTRE_A, "MEDECIN")).status()).isEqualTo(200);
        arbre("cloisonnement", cas);
        audit("audit");
        notifications("notifications");
        diffusionsCapturees("diffusions");
    }

    private Object listeLisible(Resp r) {
        if (r.status() != 200) return statutEtCode(r);
        List<String> ids = new ArrayList<>();
        JsonNode c = corps(r);
        c.forEach(a -> ids.add(a.path("id").asString()));
        return Map.of("statut", r.status(), "alertes", ids);
    }
}
