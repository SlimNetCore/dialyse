package com.hemodialyse.backend.characterization.absencespatients.consultation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code GET /api/v1/absences-patients} : liste paginee et filtrable du suivi des absences du centre courant (tri, filtres,
 * pagination, nom du patient, indicateur « en retard », garde de centre, validation des parametres).
 */
class ListeDesAbsencesCharacterizationIntegrationTest extends ConsultationAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "liste";
    }

    /**
     * 12 absences du centre A (statuts, motifs, sources, bornes du retard, patient sans prenom, prenom blanc, patient absent de la
     * table, patient d'un autre centre, date future) + 1 absence du centre B.
     */
    private void jeuDeDonnees() {
        abs(1, P_AMRANI, jour(1)).poser();
        abs(2, P_BELKACEM, jour(1)).declaree().poser();
        abs(3, P_CHIKHI, jour(3)).poser();
        abs(4, P_DAHMANI, jour(4)).poser();
        abs(5, P_AMRANI, jour(5)).statut("JUSTIFIEE").motif("MALADIE").commentaire("Grippe").declaree().poser();
        abs(6, P_BELKACEM, jour(6)).statut("NON_JUSTIFIEE").motif("NON_JUSTIFIEE").valeur("Forfait HD Premium", "6188.00", "19.00", "5200.00").poser();
        abs(7, P_CHIKHI, jour(8)).statut("RATTRAPEE").motif("HOSPITALISATION").rattrapee(jour(2)).poser();
        abs(8, P_DAHMANI, jour(9)).statut("ANNULEE").commentaire("Declaration erronee").poser();
        abs(9, P_AMRANI, jour(-2)).poser();
        abs(10, P_FANTOME, jour(10)).statut("JUSTIFIEE").motif("VOYAGE").poser();
        abs(11, P_CENTRE_B, jour(11)).poser();
        abs(12, P_AMRANI, jour(12)).poser();
        abs(13, P_CENTRE_B, jour(1)).centre(CENTRE_B).poser();
    }

    @Test
    void la_liste_est_triee_par_date_decroissante_puis_par_identifiant_avec_le_nom_du_patient_et_l_indicateur_en_retard() throws Exception {
        jeuDeDonnees();

        golden("liste_sans_filtre", doGet(ABSENCES, adminA()));
    }

    @Test
    void la_liste_du_centre_b_ne_contient_que_ses_propres_absences_et_un_centre_sans_absence_donne_une_page_vide() throws Exception {
        jeuDeDonnees();

        golden("liste_centre_b", doGet(ABSENCES, adminB()));
        golden("liste_centre_sans_absence", doGet(ABSENCES, as(CENTRE_VIDE, "ADMIN")));
    }

    @Test
    void les_filtres_statut_motif_patient_et_periode_se_cumulent_et_le_total_les_reflete() throws Exception {
        jeuDeDonnees();
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("statut=A_QUALIFIER", resumeListe(doGet(ABSENCES + "?statut=A_QUALIFIER", adminA())));
        cas.put("statut=JUSTIFIEE", resumeListe(doGet(ABSENCES + "?statut=JUSTIFIEE", adminA())));
        cas.put("statut=NON_JUSTIFIEE", resumeListe(doGet(ABSENCES + "?statut=NON_JUSTIFIEE", adminA())));
        cas.put("statut=RATTRAPEE", resumeListe(doGet(ABSENCES + "?statut=RATTRAPEE", adminA())));
        cas.put("statut=ANNULEE", resumeListe(doGet(ABSENCES + "?statut=ANNULEE", adminA())));
        cas.put("motif=MALADIE", resumeListe(doGet(ABSENCES + "?motif=MALADIE", adminA())));
        cas.put("motif=NON_JUSTIFIEE", resumeListe(doGet(ABSENCES + "?motif=NON_JUSTIFIEE", adminA())));
        cas.put("motif=DECES (aucune)", resumeListe(doGet(ABSENCES + "?motif=DECES", adminA())));
        cas.put("patientId=AMRANI", resumeListe(doGet(ABSENCES + "?patientId=" + P_AMRANI, adminA())));
        cas.put("patientId=FANTOME", resumeListe(doGet(ABSENCES + "?patientId=" + P_FANTOME, adminA())));
        cas.put("patientId=inconnu", resumeListe(doGet(ABSENCES + "?patientId=" + UUID.fromString("99998100-0000-0000-0000-00000000ffff"), adminA())));
        cas.put("patientId d'un autre centre (absence posee dans A)", resumeListe(doGet(ABSENCES + "?patientId=" + P_CENTRE_B, adminA())));
        cas.put("from=J-5 (inclus)", resumeListe(doGet(ABSENCES + "?from=" + jour(5), adminA())));
        cas.put("to=J-5 (inclus)", resumeListe(doGet(ABSENCES + "?to=" + jour(5), adminA())));
        cas.put("from=J-8 to=J-4", resumeListe(doGet(ABSENCES + "?from=" + jour(8) + "&to=" + jour(4), adminA())));
        cas.put("from=to=J-1", resumeListe(doGet(ABSENCES + "?from=" + jour(1) + "&to=" + jour(1), adminA())));
        cas.put("from apres to", resumeListe(doGet(ABSENCES + "?from=" + jour(1) + "&to=" + jour(5), adminA())));
        cas.put("from=J+1 (futur seul)", resumeListe(doGet(ABSENCES + "?from=" + jour(-1), adminA())));
        cas.put("statut=A_QUALIFIER&from=J-4&to=J-1", resumeListe(doGet(ABSENCES + "?statut=A_QUALIFIER&from=" + jour(4) + "&to=" + jour(1), adminA())));
        cas.put("statut=JUSTIFIEE&motif=VOYAGE&patientId=FANTOME", resumeListe(doGet(ABSENCES + "?statut=JUSTIFIEE&motif=VOYAGE&patientId=" + P_FANTOME, adminA())));
        cas.put("statut=A_QUALIFIER&motif=MALADIE (incoherent)", resumeListe(doGet(ABSENCES + "?statut=A_QUALIFIER&motif=MALADIE", adminA())));
        cas.put("filtres vides (statut=)", resumeListe(doGet(ABSENCES + "?statut=&motif=&patientId=", adminA())));

        goldenNode("filtres", mapper.valueToTree(cas));
    }

    @Test
    void la_pagination_decoupe_le_resultat_trie_et_ses_bornes_sont_celles_de_la_base() throws Exception {
        jeuDeDonnees();
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("defaut (page 0, size 20)", resumeListe(doGet(ABSENCES, adminA())));
        cas.put("size=5 page=0", resumeListe(doGet(ABSENCES + "?size=5&page=0", adminA())));
        cas.put("size=5 page=1", resumeListe(doGet(ABSENCES + "?size=5&page=1", adminA())));
        cas.put("size=5 page=2", resumeListe(doGet(ABSENCES + "?size=5&page=2", adminA())));
        cas.put("size=5 page=3 (au dela)", resumeListe(doGet(ABSENCES + "?size=5&page=3", adminA())));
        cas.put("size=12 (exact)", resumeListe(doGet(ABSENCES + "?size=12", adminA())));
        cas.put("size=11 page=1", resumeListe(doGet(ABSENCES + "?size=11&page=1", adminA())));
        cas.put("size=1000", resumeListe(doGet(ABSENCES + "?size=1000", adminA())));
        cas.put("size=0", resumeListe(doGet(ABSENCES + "?size=0", adminA())));
        cas.put("size=-1", resumeListe(doGet(ABSENCES + "?size=-1", adminA())));
        cas.put("page=-1", resumeListe(doGet(ABSENCES + "?page=-1", adminA())));
        cas.put("size=abc", resumeListe(doGet(ABSENCES + "?size=abc", adminA())));
        cas.put("page=abc", resumeListe(doGet(ABSENCES + "?page=abc", adminA())));
        cas.put("filtre + pagination", resumeListe(doGet(ABSENCES + "?statut=A_QUALIFIER&size=2&page=1", adminA())));

        goldenNode("pagination_nouveau_code", mapper.valueToTree(cas));
    }

    @Test
    void des_parametres_illisibles_sont_refuses_avec_le_corps_d_erreur_du_gestionnaire_global() throws Exception {
        jeuDeDonnees();

        golden("parametre_statut_inconnu_nouveau_code", doGet(ABSENCES + "?statut=PERDUE", adminA()));
        golden("parametre_statut_minuscule_nouveau_code", doGet(ABSENCES + "?statut=a_qualifier", adminA()));
        golden("parametre_motif_inconnu_nouveau_code", doGet(ABSENCES + "?motif=FATIGUE", adminA()));
        golden("parametre_patient_non_uuid_nouveau_code", doGet(ABSENCES + "?patientId=123", adminA()));
        golden("parametre_date_illisible_nouveau_code", doGet(ABSENCES + "?from=21/10/2026", adminA()));
        golden("parametre_centre_non_uuid_nouveau_code", doGet(ABSENCES + "?centerId=abc", adminA()));
    }

    @Test
    void le_centre_demande_doit_etre_celui_de_la_session() throws Exception {
        jeuDeDonnees();
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("centerId = centre de la session", resumeListe(doGet(ABSENCES + "?centerId=" + CENTRE_A, adminA())));
        cas.put("centerId absent", resumeListe(doGet(ABSENCES, adminA())));

        goldenNode("centre_de_la_session", mapper.valueToTree(cas));
        golden("centre_autre_que_celui_de_la_session", doGet(ABSENCES + "?centerId=" + CENTRE_B, adminA()));
        golden("session_sans_centre", doGet(ABSENCES, as(null, "ADMIN")));
    }

    @Test
    void chaque_ligne_porte_la_valeur_figee_les_traces_de_qualification_et_le_retard_calcule_a_partir_du_jour_utc() throws Exception {
        abs(1, P_AMRANI, jour(3)).poser();
        abs(2, P_BELKACEM, jour(4)).poser();
        abs(3, P_CHIKHI, jour(4)).statut("JUSTIFIEE").motif("TRANSPORT").poser();
        ObjectNode vue = mapper.createObjectNode();
        var items = json(doGet(ABSENCES, adminA())).path("items");
        assertEquals(3, items.size());
        String[] etiquettes = {"J-3 A_QUALIFIER", "J-4 A_QUALIFIER", "J-4 JUSTIFIEE"};
        for (int k = 0; k < 3; k++) {
            assertEquals(etiquettes[k].split(" ")[1], items.get(k).path("statut").asString());
            vue.set(etiquettes[k], items.get(k).path("enRetard"));
        }

        goldenNode("retard_a_j_plus_3_non_j_plus_4_oui_et_seulement_a_qualifier", vue);
    }
}
