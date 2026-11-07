package com.hemodialyse.backend.characterization.dossiermedical.dm5;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * DM5, lot 3 (abords vasculaires), consultation et suppression : {@code GET} et {@code DELETE} sur
 * {@code /api/v1/patients/{id}/abords-vasculaires}.
 */
class LaConsultationEtLaSuppressionDesAbordsVasculairesCharacterizationIntegrationTest extends Dm5CharacterizationSupport {

    @Override
    protected String dossier() {
        return "abords/consultation";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm5(CENTRE_A, "PAT_A");
        patientB = patientDm5(CENTRE_B, "PAT_B");
    }

    @Test
    void la_liste_est_triee_de_la_creation_la_plus_recente_et_paginee() throws Exception {
        deuxCentres();
        for (int i = 1; i <= 5; i++) {
            abordEnBase(CENTRE_A, patientA, "ABORD_" + i, "TYPE_" + i, i % 2 == 0 ? "GAUCHE" : "DROIT", null, fixe(i * 10), null, i % 2 == 1);
        }
        abordEnBase(CENTRE_A, patientB, "ABORD_AUTRE_PATIENT", "AUTRE_PATIENT", null, null, fixe(99), null, true);
        abordEnBase(CENTRE_B, patientB, "ABORD_AUTRE_CENTRE", "AUTRE_CENTRE", null, null, fixe(98), null, true);
        String url = abordsApi(patientA);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("defaut", page(doGet(url, medecinA()), "typeAbord", "dateCreation", "actif"));
        m.put("taille 2, page 0", page(doGet(url + "?size=2&page=0", medecinA()), "typeAbord"));
        m.put("taille 2, page 2", page(doGet(url + "?size=2&page=2", medecinA()), "typeAbord"));
        m.put("taille 2, page 9", page(doGet(url + "?size=2&page=9", medecinA()), "typeAbord"));
        m.put("taille 2, page -1", page(doGet(url + "?size=2&page=-1", medecinA()), "typeAbord"));
        m.put("taille 0", page(doGet(url + "?size=0", medecinA()), "typeAbord"));
        m.put("taille 1000", page(doGet(url + "?size=1000", medecinA()), "typeAbord"));
        m.put("taille illisible", refus(doGet(url + "?size=abc", medecinA())));
        m.put("liste pour l'ADMIN", page(doGet(url + "?size=1", as(CENTRE_A, "ADMIN")), "typeAbord"));
        m.put("liste pour l'INFIRMIER", page(doGet(url + "?size=1", as(CENTRE_A, "INFIRMIER")), "typeAbord"));
        m.put("liste d'un patient inconnu", page(doGet(abordsApi(idStable("patient", "inconnu")), medecinA()), "typeAbord"));
        arbre("pages", m);
    }

    @Test
    void la_lecture_rend_la_forme_complete_d_un_abord() throws Exception {
        deuxCentres();
        Resp c = doPost(abordsApi(patientA), corps("centerId", CENTRE_A, "typeAbord", "FAV", "cote", "GAUCHE", "localisation", "Avant-bras fictif",
                "dateCreation", fixe(-60), "dateFin", fixe(-30), "actif", false, "complications", "Complication fictive"), medecinA());
        aliasNommes.put(idDe(c), "<ABORD_1>");
        abordEnBase(CENTRE_A, patientA, "ABORD_2", "KT_AIGU", null, null, fixe(-10), null, true);
        lecture("liste", doGet(abordsApi(patientA), medecinA()));
    }

    @Test
    void la_suppression_rend_204_meme_quand_rien_n_est_supprime() throws Exception {
        deuxCentres();
        UUID a1 = abordEnBase(CENTRE_A, patientA, "ABORD_1", "FAV", null, null, fixe(10), null, true);
        abordEnBase(CENTRE_A, patientA, "ABORD_2", "PTFE", null, null, fixe(20), null, true);
        UUID autrePatient = abordEnBase(CENTRE_A, patientB, "ABORD_AUTRE_PATIENT", "FAV", null, null, fixe(30), null, true);
        UUID autreCentre = abordEnBase(CENTRE_B, patientB, "ABORD_AUTRE_CENTRE", "FAV", null, null, fixe(40), null, true);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("1 suppression d'un abord", refus(doDelete(abordApi(patientA, a1), medecinA())));
        cas.put("2 meme suppression rejouee", refus(doDelete(abordApi(patientA, a1), medecinA())));
        cas.put("3 identifiant inconnu", refus(doDelete(abordApi(patientA, idStable("abord", "inconnu")), medecinA())));
        cas.put("4 abord d'un autre patient du centre", refus(doDelete(abordApi(patientA, autrePatient), medecinA())));
        cas.put("5 abord d'un autre centre (centre B demande)", refus(doDelete(abordApi(patientB, autreCentre) + "?centerId=" + CENTRE_B, medecinA())));
        cas.put("6 identifiant illisible", refus(doDelete(abordsApi(patientA) + "/abc", medecinA())));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("cas", cas);
        m.put("etat", etatDesAbords());
        arbre("suppression", m);
    }
}
