package com.hemodialyse.backend.characterization.absencespatients.consultation;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Lectures de {@code absence_patient} par d'autres modules que le suivi : {@code GET /api/v1/direction/absences}
 * ({@code DirectionAbsencesQueryService}, parties non financieres) et, dans {@code SeanceRestController}, le tableau de bord
 * ({@code /api/v1/seances/dashboard}, sa version exportee CSV) et ses details ({@code /dashboard/details?kind=absence}).
 * Prepare la slice A4 (lectures publiees). Mars 2026 entier (ancre = dimanche 1er mars 2026), donc independant du jour d'execution.
 */
class LecturesDeAbsencesParLaDirectionEtLaSeanceCharacterizationIntegrationTest extends ConsultationAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "lectures-publiees";
    }

    /**
     * Centre A. Fevrier (hors mars) : 2 absences. Mars : 5 JUSTIFIEE MALADIE, 2 NON_JUSTIFIEE, 1 A_QUALIFIER sans motif, 1 RATTRAPEE
     * (valeur elevee, jamais valorisee), 1 ANNULEE (exclue), 1 JUSTIFIEE VOYAGE le 31 mars, 1 absence d'un patient absent de la table
     * (comptee, absente des details). Avril : 6 JUSTIFIEE FAMILIAL (effectif non masque). Seances realisees de mars : 12 (+ CREE et
     * ABSENT non comptees). Centre B : 1 absence de mars et 3 seances.
     */
    private void jeuDeDonnees() {
        int r = 0;
        ancre(0);
        abs(++r, P_AMRANI, ancre(-1)).statut("JUSTIFIEE").motif("MALADIE").valeur("Forfait HD", "119.00", "19.00", "100.00").poser();
        abs(++r, P_BELKACEM, ancre(-2)).valeur("Forfait HD", "119.00", "19.00", "100.00").poser();
        for (int j = 1; j <= 5; j++) {
            abs(++r, P_AMRANI, ancre(j)).statut("JUSTIFIEE").motif("MALADIE").valeur("Forfait HD", "119.00", "19.00", "100.00").poser();
        }
        abs(++r, P_BELKACEM, ancre(1)).statut("NON_JUSTIFIEE").motif("NON_JUSTIFIEE").valeur("Forfait HD", "238.00", "19.00", "200.00").poser();
        abs(++r, P_BELKACEM, ancre(2)).statut("NON_JUSTIFIEE").motif("NON_JUSTIFIEE").valeur("Forfait HD", "238.00", "19.00", "200.00").poser();
        abs(++r, P_CHIKHI, ancre(1)).valeur("Forfait HD", "59.50", "19.00", "50.00").poser();
        abs(++r, P_CHIKHI, ancre(2)).statut("RATTRAPEE").motif("VOYAGE").rattrapee(ancre(9)).valeur("Forfait HD", "999.00", "19.00", "839.50").poser();
        abs(++r, P_DAHMANI, ancre(1)).statut("ANNULEE").commentaire("Erreur").poser();
        abs(++r, P_DAHMANI, ancre(30)).statut("JUSTIFIEE").motif("VOYAGE").valeur("Forfait HD", "10.00", "0.00", "10.00").poser();
        abs(++r, P_FANTOME, ancre(3)).statut("JUSTIFIEE").motif("TRANSPORT").valeur("Forfait HD", "119.00", "19.00", "100.00").poser();
        for (int j = 31; j <= 34; j++) {
            abs(++r, P_AMRANI, ancre(j)).statut("JUSTIFIEE").motif("FAMILIAL").valeur("Forfait HD", "119.00", "19.00", "100.00").poser();
        }
        abs(++r, P_BELKACEM, ancre(31)).statut("JUSTIFIEE").motif("FAMILIAL").valeur("Forfait HD", "119.00", "19.00", "100.00").poser();
        abs(++r, P_BELKACEM, ancre(32)).statut("JUSTIFIEE").motif("FAMILIAL").valeur("Forfait HD", "119.00", "19.00", "100.00").poser();
        abs(++r, P_CENTRE_B, ancre(1)).centre(CENTRE_B).statut("JUSTIFIEE").motif("MALADIE").valeur("Forfait B", "50.00", "0.00", "50.00").poser();
        for (int j = 6; j <= 12; j++) {
            seance(P_AMRANI, CENTRE_A, ancre(j), "VALIDEE");
        }
        for (int j = 6; j <= 8; j++) {
            seance(P_BELKACEM, CENTRE_A, ancre(j), "SIGNEE");
        }
        seance(P_CHIKHI, CENTRE_A, ancre(6), "FACTUREE");
        seance(P_CHIKHI, CENTRE_A, ancre(7), "CREE");
        seance(P_DAHMANI, CENTRE_A, ancre(6), "VALIDEE");
        seance(P_DAHMANI, CENTRE_A, ancre(7), "ABSENT");
        seance(P_CENTRE_B, CENTRE_B, ancre(6), "VALIDEE");
        seance(P_CENTRE_B, CENTRE_B, ancre(7), "VALIDEE");
        seance(P_CENTRE_B, CENTRE_B, ancre(8), "VALIDEE");
    }

    private String periodeDirection() {
        return "?from=" + ancre(-14) + "&to=" + ancre(45);
    }

    // ------------------------------------------------------------------ Direction

    @Test
    void la_direction_compte_les_absences_par_statut_et_motif_valorise_les_non_rattrapees_et_masque_les_petits_effectifs() throws Exception {
        jeuDeDonnees();

        // KB-ABS-07 corrigé : les stats d'un effectif de 1 à 4 sont masquées (golden _nouveau_code)
        golden("direction_absences_fevrier_avril_nouveau_code", doGet(DIRECTION + "/absences" + periodeDirection(), direction()));
    }

    @Test
    void la_direction_borne_la_periode_a_mars_et_calcule_le_taux_d_absenteisme_sur_les_absences_comptabilisees() throws Exception {
        jeuDeDonnees();

        golden("direction_absences_mars_seul_nouveau_code", doGet(DIRECTION + "/absences?from=" + ancre(0) + "&to=" + ancre(30), direction()));
        golden("direction_absences_un_jour_nouveau_code",doGet(DIRECTION + "/absences?from=" + ancre(1) + "&to=" + ancre(1), direction()));
        golden("direction_absences_periode_sans_absence_nouveau_code", doGet(DIRECTION + "/absences?from=" + ancre(60) + "&to=" + ancre(70), direction()));
    }

    @Test
    void sans_dates_la_direction_prend_l_annee_en_cours_jusqu_a_aujourd_hui_utc() throws Exception {
        jeuDeDonnees();
        JsonNode corps = json(doGet(DIRECTION + "/absences", direction()));

        assertEquals(LocalDate.of(aujourdhui().getYear(), 1, 1).toString(), corps.path("from").asString());
        assertEquals(aujourdhui().toString(), corps.path("to").asString());
    }

    // ------------------------------------------------------------------ Seance : tableau de bord

    @Test
    void le_tableau_de_bord_des_seances_compte_toutes_les_absences_non_annulees_y_compris_rattrapees_et_d_un_patient_absent_de_la_table() throws Exception {
        jeuDeDonnees();

        golden("tableau_de_bord_mars_centre_a", doGet(SEANCES + "/dashboard?centerId=" + CENTRE_A + "&year=2026&month=3", adminA()));
        golden("tableau_de_bord_mars_centre_b", doGet(SEANCES + "/dashboard?centerId=" + CENTRE_B + "&year=2026&month=3", adminB()));
        golden("tableau_de_bord_mois_sans_absence", doGet(SEANCES + "/dashboard?centerId=" + CENTRE_A + "&year=2026&month=5", adminA()));
        golden("tableau_de_bord_fevrier", doGet(SEANCES + "/dashboard?centerId=" + CENTRE_A + "&year=2026&month=2", adminA()));
    }

    @Test
    void le_tableau_de_bord_refuse_un_centre_autre_que_celui_de_la_session_et_son_export_csv_reprend_le_nombre_d_absences() throws Exception {
        jeuDeDonnees();

        // KB-ABS-05 corrigé : garde de centre standard (403) sur le tableau de bord, ses détails et son export
        golden("tableau_de_bord_centre_b_lu_par_l_administrateur_du_centre_a_nouveau_code", doGet(SEANCES + "/dashboard?centerId=" + CENTRE_B + "&year=2026&month=3", adminA()));
        golden("details_centre_b_lus_par_l_administrateur_du_centre_a_nouveau_code", doGet(SEANCES + "/dashboard/details?centerId=" + CENTRE_B + "&year=2026&month=3&kind=absence", adminA()));
        golden("export_centre_b_lu_par_l_administrateur_du_centre_a_nouveau_code", doGet(SEANCES + "/dashboard/export?centerId=" + CENTRE_B + "&year=2026&month=3", adminA()));
        goldenTexte("tableau_de_bord_export_csv_mars", doGet(SEANCES + "/dashboard/export?centerId=" + CENTRE_A + "&year=2026&month=3", adminA()));
    }

    @Test
    void les_parametres_invalides_du_tableau_de_bord_sont_refuses() throws Exception {
        golden("tableau_de_bord_mois_13", doGet(SEANCES + "/dashboard?centerId=" + CENTRE_A + "&year=2026&month=13", adminA()));
        golden("tableau_de_bord_sans_centre", doGet(SEANCES + "/dashboard?year=2026&month=3", adminA()));
        golden("tableau_de_bord_annee_illisible", doGet(SEANCES + "/dashboard?centerId=" + CENTRE_A + "&year=abc&month=3", adminA()));
    }

    // ------------------------------------------------------------------ Seance : details

    @Test
    void les_details_des_absences_listent_les_absences_non_annulees_des_patients_connus_tries_par_date_nom_prenom() throws Exception {
        jeuDeDonnees();

        golden("details_absences_mars", doGet(SEANCES + "/dashboard/details?centerId=" + CENTRE_A + "&year=2026&month=3&kind=absence", adminA()));
        golden("details_presences_mars", doGet(SEANCES + "/dashboard/details?centerId=" + CENTRE_A + "&year=2026&month=3&kind=presence", adminA()));
        golden("details_absences_centre_b", doGet(SEANCES + "/dashboard/details?centerId=" + CENTRE_B + "&year=2026&month=3&kind=absence", adminB()));
    }

    @Test
    void le_type_de_details_est_insensible_a_la_casse_et_aux_espaces_et_un_type_inconnu_est_refuse() throws Exception {
        jeuDeDonnees();
        Map<String, Object> cas = new LinkedHashMap<>();
        for (String kind : List.of("ABSENCE", "%20absence%20", "Absence")) {
            cas.put("kind=" + kind, json(doGet(SEANCES + "/dashboard/details?centerId=" + CENTRE_A + "&year=2026&month=3&kind=" + kind, adminA())).path("items").size());
        }

        goldenNode("details_type_normalise_nombre_d_absences", mapper.valueToTree(cas));
        golden("details_type_inconnu", doGet(SEANCES + "/dashboard/details?centerId=" + CENTRE_A + "&year=2026&month=3&kind=retard", adminA()));
        golden("details_type_absent", doGet(SEANCES + "/dashboard/details?centerId=" + CENTRE_A + "&year=2026&month=3", adminA()));
    }

    // ------------------------------------------------------------------ profils

    @Test
    void les_profils_autorises_sur_les_lectures_publiees_de_la_seance_et_de_la_direction() throws Exception {
        jeuDeDonnees();
        Map<String, RequestPostProcessor> profils = new LinkedHashMap<>();
        profils.put("ADMIN", adminA());
        profils.put("SECRETAIRE", as(CENTRE_A, "SECRETAIRE"));
        profils.put("MEDECIN", as(CENTRE_A, "MEDECIN"));
        profils.put("INFIRMIER", as(CENTRE_A, "INFIRMIER"));
        profils.put("DIRECTION", direction());
        profils.put("SUPERADMIN", as(CENTRE_A, "SUPERADMIN"));
        Map<String, Object> matrice = new LinkedHashMap<>();
        String seancesQuery = "?centerId=" + CENTRE_A + "&year=2026&month=3";
        for (Map.Entry<String, RequestPostProcessor> p : profils.entrySet()) {
            matrice.put("GET /seances/dashboard [" + p.getKey() + "]", doGet(SEANCES + "/dashboard" + seancesQuery, p.getValue()).status());
            matrice.put("GET /seances/dashboard/details [" + p.getKey() + "]",
                    doGet(SEANCES + "/dashboard/details" + seancesQuery + "&kind=absence", p.getValue()).status());
            matrice.put("GET /direction/absences [" + p.getKey() + "]",
                    doGet(DIRECTION + "/absences" + periodeDirection(), p.getValue()).status());
        }

        goldenNode("matrice_profils", mapper.valueToTree(matrice));
    }
}
