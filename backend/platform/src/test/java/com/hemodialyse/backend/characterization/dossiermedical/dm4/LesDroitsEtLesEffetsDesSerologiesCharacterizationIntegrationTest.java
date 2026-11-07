package com.hemodialyse.backend.characterization.dossiermedical.dm4;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM4, lot 3 : droits par profil, cloisonnement des centres, audit {@code SEROLOGIE_*}, événements et notifications.
 */
class LesDroitsEtLesEffetsDesSerologiesCharacterizationIntegrationTest extends SerologieCharacterizationSupport {

    @Override
    protected String dossier() {
        return "droits";
    }

    private UUID patientA;
    private UUID patientB;
    private UUID serologieA;

    private void deuxCentresEtUneSerologie() {
        patientA = patientDm4(CENTRE_A, "PAT_A");
        patientB = patientDm4(CENTRE_B, "PAT_B");
        serologieA = serologieEnBase(CENTRE_A, patientA, "SERO_A", "TPHA", "NEGATIF", fixe(2), null);
    }

    @Test
    void les_droits_par_profil_sur_les_cinq_routes() throws Exception {
        deuxCentresEtUneSerologie();
        Map<String, Object> profils = new LinkedHashMap<>();
        for (Map.Entry<String, RequestPostProcessor> p : profilsDm4().entrySet()) {
            nettoyerDm4();
            deuxCentresEtUneSerologie();
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("liste", statutEtCode(doGet(serologiesApi(patientA), p.getValue())));
            ligne.put("synthese", statutEtCode(doGet(serologiesApi(patientA) + "/synthese", p.getValue())));
            ligne.put("creation", statutEtCode(doPost(serologiesApi(patientA), corps(CENTRE_A, "AG_HBS", "NEGATIF", fixe(3), null), p.getValue())));
            ligne.put("correction", statutEtCode(doPut(serologieApi(patientA, serologieA), corpsMaj(CENTRE_A, "DOUTEUX", null), p.getValue())));
            ligne.put("suppression", statutEtCode(doDelete(serologieApi(patientA, serologieA), p.getValue())));
            profils.put(p.getKey(), ligne);
        }
        nettoyerDm4();
        deuxCentresEtUneSerologie();
        Map<String, Object> anonyme = new LinkedHashMap<>();
        anonyme.put("liste", statutEtCode(doGetAnonymous(serologiesApi(patientA))));
        anonyme.put("synthese", statutEtCode(doGetAnonymous(serologiesApi(patientA) + "/synthese")));
        anonyme.put("creation", statutEtCode(appel("POST", serologiesApi(patientA), corps(CENTRE_A, "AG_HBS", "NEGATIF", fixe(3), null), null)));
        anonyme.put("correction", statutEtCode(appel("PUT", serologieApi(patientA, serologieA), corpsMaj(CENTRE_A, "DOUTEUX", null), null)));
        anonyme.put("suppression", statutEtCode(appel("DELETE", serologieApi(patientA, serologieA), null, null)));
        profils.put("anonyme", anonyme);
        arbre("profils", profils);
        assertThat(resultatEnBase(serologieA)).isEqualTo("NEGATIF");
        assertThat(nombreDeSerologies()).isEqualTo(1);
    }

    @Test
    void un_refus_de_droit_n_ecrit_rien_mais_un_corps_ou_une_pagination_illisible_donne_500_avant_le_controle_des_droits() throws Exception {
        deuxCentresEtUneSerologie();
        String avant = empreinte();
        Map<String, String> cas = new LinkedHashMap<>();
        cas.put("INFIRMIER : creation", statutEtCode(doPost(serologiesApi(patientA), corps(CENTRE_A, "AG_HBS", "NEGATIF", fixe(3), null),
                as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : creation avec un corps illisible", statutEtCode(doPost(serologiesApi(patientA), "{pas du json", as(CENTRE_A, "INFIRMIER"))));
        cas.put("INFIRMIER : correction avec un corps absent", statutEtCode(doPut(serologieApi(patientA, serologieA), "", as(CENTRE_A, "INFIRMIER"))));
        cas.put("ADMIN : creation", statutEtCode(doPost(serologiesApi(patientA), corps(CENTRE_A, "AG_HBS", "NEGATIF", fixe(3), null),
                as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : suppression", statutEtCode(doDelete(serologieApi(patientA, serologieA), as(CENTRE_A, "ADMIN"))));
        cas.put("ADMIN : liste avec une pagination illisible", statutEtCode(doGet(serologiesApi(patientA) + "?size=abc", as(CENTRE_A, "ADMIN"))));
        cas.put("INFIRMIER : liste avec une pagination illisible", statutEtCode(doGet(serologiesApi(patientA) + "?size=abc", as(CENTRE_A, "INFIRMIER"))));
        // KB-MED-47 corrigé : 403 AVANT 400 (un profil sans droit n'apprend plus rien, avant : 500) ; ADMIN (droit de lecture) : 400. Legacy archivé dans dm4/_ecarts-legacy.
        arbre("refus_code_nouveau_code", cas);
        assertThat(empreinte()).isEqualTo(avant);
    }

    @Test
    void le_cloisonnement_par_centre_repose_sur_le_centre_de_la_session_et_la_recherche_par_centre() throws Exception {
        deuxCentresEtUneSerologie();
        UUID serologieB = serologieEnBase(CENTRE_B, patientB, "SERO_B", "TPHA", "NEGATIF", fixe(2), null);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("liste avec centerId du centre B (session A)", statutEtCode(doGet(serologiesApi(patientB) + "?centerId=" + CENTRE_B, medecinA())));
        cas.put("synthese avec centerId du centre B (session A)", statutEtCode(doGet(serologiesApi(patientB) + "/synthese?centerId=" + CENTRE_B,
                medecinA())));
        cas.put("creation avec le centre B dans le corps (session A)", statutEtCode(doPost(serologiesApi(patientB),
                corps(CENTRE_B, "AG_HBS", "NEGATIF", fixe(3), null), medecinA())));
        cas.put("creation sans centre dans le corps (centre de la session)", statutEtCode(doPost(serologiesApi(patientA),
                corps(null, "AG_HBS", "NEGATIF", fixe(3), null), medecinA())));
        cas.put("correction avec le centre B (session A)", statutEtCode(doPut(serologieApi(patientB, serologieB),
                corpsMaj(CENTRE_B, "DOUTEUX", null), medecinA())));
        cas.put("suppression avec centerId B (session A)", statutEtCode(doDelete(serologieApi(patientB, serologieB) + "?centerId=" + CENTRE_B,
                medecinA())));
        cas.put("MEDECIN sans centre de session : liste", statutEtCode(doGet(serologiesApi(patientA), as(null, "MEDECIN"))));
        cas.put("MEDECIN sans centre de session : creation", statutEtCode(doPost(serologiesApi(patientA),
                corps(CENTRE_A, "AG_HBS", "NEGATIF", fixe(4), null), as(null, "MEDECIN"))));
        cas.put("SUPERADMIN + MEDECIN lit le centre B depuis la session A", statutEtCode(doGet(serologiesApi(patientB) + "?centerId=" + CENTRE_B,
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("SUPERADMIN + MEDECIN cree dans le centre B pour un patient du centre B", statutEtCode(doPost(serologiesApi(patientB),
                corps(CENTRE_B, "AG_HBS", "NEGATIF", fixe(5), null), as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("SUPERADMIN + MEDECIN cree dans le centre B pour un patient du centre A", statutEtCode(doPost(serologiesApi(patientA),
                corps(CENTRE_B, "AG_HBS", "NEGATIF", fixe(6), null), as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("SUPERADMIN + MEDECIN corrige la sérologie du centre B", statutEtCode(doPut(serologieApi(patientB, serologieB),
                corpsMaj(CENTRE_B, "DOUTEUX", null), as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        cas.put("SUPERADMIN + MEDECIN supprime dans le centre B", statutEtCode(doDelete(serologieApi(patientB, serologieB) + "?centerId=" + CENTRE_B,
                as(CENTRE_A, "SUPERADMIN", "MEDECIN"))));
        arbre("cloisonnement", cas);
        etatDesSerologies("serologies");
    }

    @Test
    void les_ecritures_et_leurs_refus_sont_journalises_sous_SEROLOGIE_et_les_lectures_ne_le_sont_pas() throws Exception {
        deuxCentresEtUneSerologie();
        monterLAudit();
        Resp c = doPost(serologiesApi(patientA), corps(CENTRE_A, "VIH_AC", "NEGATIF", fixe(3), null), medecinA());
        UUID s = UUID.fromString(idDe(c));
        aliasNommes.put(s.toString(), "<SERO>");
        doPost(serologiesApi(patientA), corps(CENTRE_A, "VIH_AC", "NEGATIF", fixe(3), null), medecinA());
        doPost(serologiesApi(patientA), corps(CENTRE_A, "VIH_AC", "POSITIF", fixe(4), null), medecinA());
        doPut(serologieApi(patientA, s), corpsMaj(CENTRE_A, "DOUTEUX", null), medecinA());
        doPut(serologieApi(patientA, UUID.nameUUIDFromBytes("x".getBytes())), corpsMaj(CENTRE_A, "DOUTEUX", null), medecinA());
        doGet(serologiesApi(patientA), medecinA());
        doGet(serologiesApi(patientA) + "/synthese", as(CENTRE_A, "ADMIN"));
        doPost(serologiesApi(patientA), corps(CENTRE_A, "AG_HBS", "NEGATIF", fixe(3), null), as(CENTRE_A, "INFIRMIER"));
        doDelete(serologieApi(patientA, s), as(CENTRE_A, "ADMIN"));
        doDelete(serologieApi(patientA, s), medecinA());
        doDelete(serologieApi(patientA, s), medecinA());
        // KB-MED-49 partiel : un DELETE qui n'a rien supprimé (204) n'est plus audité SEROLOGIE_SUPPRIMEE. Legacy archivé dans dm4/_ecarts-legacy.
        audit("audit_nouveau_code");
        sql("audit_detail_nouveau_code", "SELECT action_code, status_code, COUNT(*) AS nombre FROM audit_log WHERE center_id IN ('" + CENTRE_A + "','"
                + CENTRE_B + "') GROUP BY action_code, status_code ORDER BY action_code, status_code");
    }

    @Test
    void la_serologie_positive_detectee_est_un_evenement_de_domaine_que_personne_ne_publie() throws Exception {
        deuxCentresEtUneSerologie();
        monterLAudit();
        doPost(serologiesApi(patientA), corps(CENTRE_A, "AG_HBS", "POSITIF", fixe(3), CONDUITE), medecinA());
        UUID s = serologieEnBase(CENTRE_A, patientA, "SERO_C", "VIH_AC", "NEGATIF", fixe(4), null);
        doPut(serologieApi(patientA, s), corpsMaj(CENTRE_A, "POSITIF", CONDUITE), medecinA());
        // SerologiePositiveDetectee est collecte par l'agregat mais pullEvents() n'est jamais appele (KB-MED-46) : aucune diffusion,
        // et la seule notification durable eventuelle vient de la reaffectation en isolement (Planification), pas de la serologie.
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("diffusions", new ArrayList<>(diffusions));
        arbre("evenements", m);
        notifications("notifications");
    }
}
