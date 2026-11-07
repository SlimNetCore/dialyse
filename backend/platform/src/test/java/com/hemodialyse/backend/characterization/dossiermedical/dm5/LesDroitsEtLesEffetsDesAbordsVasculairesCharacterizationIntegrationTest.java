package com.hemodialyse.backend.characterization.dossiermedical.dm5;

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
 * DM5, lot 3 (abords vasculaires) : droits par profil (lecture ouverte à l'infirmier), cloisonnement des centres, audit, export FHIR
 * (Procedure), diffusions et notifications.
 */
class LesDroitsEtLesEffetsDesAbordsVasculairesCharacterizationIntegrationTest extends Dm5CharacterizationSupport {

    @Override
    protected String dossier() {
        return "abords/droits";
    }

    private UUID patientA;
    private UUID patientB;
    private UUID abordA;

    private void deuxCentresEtUnAbord() {
        patientA = patientDm5(CENTRE_A, "PAT_A");
        patientB = patientDm5(CENTRE_B, "PAT_B");
        abordA = abordEnBase(CENTRE_A, patientA, "ABORD_A", "FAV", "GAUCHE", null, fixe(10), null, true);
    }

    private String nouveau(UUID centre, String type) {
        return corps("centerId", centre, "typeAbord", type, "dateCreation", fixe(20));
    }

    @Test
    void les_droits_par_profil_sur_les_quatre_routes() throws Exception {
        deuxCentresEtUnAbord();
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm5().entrySet()) {
            nettoyerDm5();
            deuxCentresEtUnAbord();
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("liste", statutEtCode(doGet(abordsApi(patientA), p.getValue())));
            ligne.put("creation", statutEtCode(doPost(abordsApi(patientA), nouveau(CENTRE_A, "CREE"), p.getValue())));
            ligne.put("correction", statutEtCode(doPut(abordApi(patientA, abordA), nouveau(CENTRE_A, "CORRIGE"), p.getValue())));
            ligne.put("suppression", statutEtCode(doDelete(abordApi(patientA, abordA), p.getValue())));
            profils.put(p.getKey(), ligne);
        }
        nettoyerDm5();
        deuxCentresEtUnAbord();
        Map<String, Object> anonyme = new LinkedHashMap<>();
        anonyme.put("liste", statutEtCode(doGetAnonymous(abordsApi(patientA))));
        anonyme.put("creation", statutEtCode(appel("POST", abordsApi(patientA), nouveau(CENTRE_A, "CREE"), null)));
        anonyme.put("correction", statutEtCode(appel("PUT", abordApi(patientA, abordA), nouveau(CENTRE_A, "CORRIGE"), null)));
        anonyme.put("suppression", statutEtCode(appel("DELETE", abordApi(patientA, abordA), null, null)));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
        assertThat(nombreDe("abords_vasculaires", CENTRE_A)).isEqualTo(1);
    }

    @Test
    void un_refus_de_droit_n_ecrit_rien_et_precede_ou_suit_la_lecture_du_corps() throws Exception {
        deuxCentresEtUnAbord();
        String avant = empreinte("abords_vasculaires");
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("INFIRMIER : creation", statutEtCode(doPost(abordsApi(patientA), nouveau(CENTRE_A, "X"), as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : creation avec un corps illisible", statutEtCode(doPost(abordsApi(patientA), "{pas du json", as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : creation avec un corps vide {}", statutEtCode(doPost(abordsApi(patientA), "{}", as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : correction avec un corps absent", statutEtCode(doPut(abordApi(patientA, abordA), "", as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : suppression", statutEtCode(doDelete(abordApi(patientA, abordA), as(CENTRE_A, "INFIRMIER"))));
        cas.put("ADMIN : creation", statutEtCode(doPost(abordsApi(patientA), nouveau(CENTRE_A, "X"), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : correction", statutEtCode(doPut(abordApi(patientA, abordA), nouveau(CENTRE_A, "X"), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : suppression", statutEtCode(doDelete(abordApi(patientA, abordA), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : liste avec une pagination illisible", statutEtCode(doGet(abordsApi(patientA) + "?size=abc", as(CENTRE_A, "ADMIN"))));
        cas.put("INFIRMIER : liste avec une pagination illisible", statutEtCode(doGet(abordsApi(patientA) + "?size=abc", as(CENTRE_A, "INFIRMIER"))));
        cas.put("SECRETAIRE : liste", statutEtCode(doGet(abordsApi(patientA), as(CENTRE_A, "SECRETAIRE"))));
        cas.put("DIRECTION : creation", statutEtCode(doPost(abordsApi(patientA), nouveau(CENTRE_A, "X"), direction())));
        // KB-MED-58 corrigé : 403 AVANT 400 (un profil sans droit n'apprend plus rien, avant : 500) ; ADMIN et INFIRMIER (droit de lecture) : 400. Legacy archivé dans dm5/abords/_ecarts-legacy.
        arbre("refus_nouveau_code", cas);
        assertThat(empreinte("abords_vasculaires")).isEqualTo(avant);
    }

    @Test
    void le_cloisonnement_par_centre_repose_sur_le_centre_de_la_session_et_la_recherche_par_centre() throws Exception {
        deuxCentresEtUnAbord();
        UUID abordB = abordEnBase(CENTRE_B, patientB, "ABORD_B", "KT_AIGU", null, null, fixe(11), null, true);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("01 liste avec centerId du centre B (session A)", statutEtCode(doGet(abordsApi(patientB) + "?centerId=" + CENTRE_B, medecinA())));
        cas.put("02 creation avec le centre B dans le corps (session A)", statutEtCode(doPost(abordsApi(patientB), nouveau(CENTRE_B, "B1"), medecinA())));
        cas.put("03 creation sans centre dans le corps (centre de la session)", statutEtCode(doPost(abordsApi(patientA),
                corps("typeAbord", "SANS_CENTRE", "dateCreation", fixe(20)), medecinA())));
        cas.put("04 correction avec le centre B (session A)", statutEtCode(doPut(abordApi(patientB, abordB), nouveau(CENTRE_B, "CORRIGE_B"), medecinA())));
        cas.put("05 suppression avec centerId B (session A)", statutEtCode(doDelete(abordApi(patientB, abordB) + "?centerId=" + CENTRE_B, medecinA())));
        cas.put("06 MEDECIN sans centre de session : liste", statutEtCode(doGet(abordsApi(patientA), as(null, "MEDECIN"))));
        cas.put("07 MEDECIN sans centre de session : creation", statutEtCode(doPost(abordsApi(patientA), nouveau(CENTRE_A, "SANS_SESSION"),
                as(null, "MEDECIN"))));
        cas.put("08 INFIRMIER sans centre de session : liste", statutEtCode(doGet(abordsApi(patientA), as(null, "INFIRMIER"))));
        cas.put("09 SUPERADMIN + MEDECIN lit le centre B depuis la session A", statutEtCode(doGet(abordsApi(patientB) + "?centerId=" + CENTRE_B,
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("10 session B lit le patient du centre A (liste vide)", statutEtCode(doGet(abordsApi(patientA), as(CENTRE_B, "MEDECIN"))));
        cas.put("11 session B ecrit pour le patient du centre A", statutEtCode(doPost(abordsApi(patientA), nouveau(CENTRE_B, "B_SUR_A"),
                as(CENTRE_B, "MEDECIN"))));
        cas.put("12 session B remplace l'abord du centre A (la ligne change de centre)", statutEtCode(doPut(abordApi(patientA, abordA),
                nouveau(CENTRE_B, "PRIS_PAR_B"), as(CENTRE_B, "MEDECIN"))));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAbords());
        // KB-MED-61 et 62 corrigés : la session B n'écrit plus pour un patient du centre A (404) et ne remplace plus l'abord du centre A (404, avant : 200 et la ligne changeait de centre). Legacy archivé dans dm5/abords/_ecarts-legacy.
        arbre("cloisonnement_nouveau_code", m);
    }

    @Test
    void les_ecritures_et_leurs_refus_sont_journalises_sous_PATIENTS_et_les_lectures_ne_le_sont_pas() throws Exception {
        deuxCentresEtUnAbord();
        monterLAudit();
        Resp c = doPost(abordsApi(patientA), nouveau(CENTRE_A, "JOURNAL"), medecinA());
        UUID s = UUID.fromString(idDe(c));
        aliasNommes.put(s.toString(), "<ABORD>");
        aliasNommes.put(idStable("abord", "inconnu").toString(), "<ABORD_INCONNU>");
        doPut(abordApi(patientA, s), nouveau(CENTRE_A, "CORRIGE"), medecinA());
        doPut(abordApi(patientA, idStable("abord", "inconnu")), nouveau(CENTRE_A, "CREE_PAR_PUT"), medecinA());
        doPut(abordApi(patientA, s), corps("centerId", CENTRE_A), medecinA());
        doGet(abordsApi(patientA), medecinA());
        doGet(abordsApi(patientA), as(CENTRE_A, "INFIRMIER"));
        doPost(abordsApi(patientA), nouveau(CENTRE_A, "REFUSE"), as(CENTRE_A, "INFIRMIER"));
        doDelete(abordApi(patientA, s), as(CENTRE_A, "ADMIN"));
        doDelete(abordApi(patientA, s), medecinA());
        doDelete(abordApi(patientA, s), medecinA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("journal", journalDAudit());
        // KB-MED-60 partiel et KB-MED-62 : un DELETE qui n'a rien supprimé (204) n'est plus audité ; un PUT corps illisible = 400 (avant : 500) et un PUT d'abord introuvable = 404. Legacy archivé dans dm5/abords/_ecarts-legacy.
        arbre("audit_nouveau_code", m);
    }

    @Test
    void les_abords_sont_exportes_en_procedure_fhir_sans_diffusion_ni_notification() throws Exception {
        deuxCentresEtUnAbord();
        monterLAudit();
        diffusions.clear();
        Resp c = doPost(abordsApi(patientA), corps("centerId", CENTRE_A, "typeAbord", "FAV", "cote", "GAUCHE", "localisation", "Avant-bras fictif",
                "dateCreation", fixe(-60), "dateFin", fixe(-30), "actif", false, "complications", "Complication fictive"), medecinA());
        aliasNommes.put(idDe(c), "<ABORD_COMPLET>");
        abordEnBase(CENTRE_A, patientA, "ABORD_SANS_COTE", "KT_AIGU", null, "Jugulaire fictive", fixe(-10), null, true);
        abordEnBase(CENTRE_A, patientA, "ABORD_NU", "PTFE", null, null, null, null, true);
        abordEnBase(CENTRE_B, patientB, "ABORD_AUTRE_CENTRE", "FAV", null, null, fixe(-20), null, true);
        String export = PATIENTS_API + "/" + patientA + "/dossier-medical/export-fhir";
        Resp bundle = doGet(export, medecinA());
        List<JsonNode> procedures = new ArrayList<>();
        List<String> types = new ArrayList<>();
        corps(bundle).path("entry").forEach(e -> {
            types.add(e.path("resource").path("resourceType").asString());
            if ("Procedure".equals(e.path("resource").path("resourceType").asString())) procedures.add(e.path("resource"));
        });
        // l'ordre des abords sans date de creation depend du SGBD (NULL en tete en PostgreSQL, en queue en H2) : on trie par type
        procedures.sort(java.util.Comparator.comparing(n -> n.path("code").path("text").asString()));
        types.sort(java.util.Comparator.naturalOrder());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("export MEDECIN", bundle.status());
        m.put("types des ressources (triees)", types);
        m.put("procedures (triees par type)", procedures);
        m.put("diffusions", new ArrayList<>(diffusions));
        m.put("notifications", notificationsDurables());
        arbre("fhir", m);
    }
}
