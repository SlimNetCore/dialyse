package com.hemodialyse.backend.characterization.absencespatients.consultation;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code GET /api/v1/absences-patients/semaine} (semaine dimanche -> samedi, absences non annulees et seances realisees) et
 * {@code GET /api/v1/absences-patients/synthese} (a qualifier / en retard). La semaine est verrouillee sur une ancre qui est un
 * dimanche fixe du passe ; le cas « sans date » est verifie par rapport au jour d'execution du centre.
 */
class SuiviDeLaSemaineEtSyntheseCharacterizationIntegrationTest extends ConsultationAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "semaine-et-synthese";
    }

    /** Semaine ANCRE..ANCRE+6 : bornes incluses, voisins exclus, statuts, centre B, seances de tous statuts. */
    private void semaineAncree() {
        abs(1, P_AMRANI, ancre(-1)).poser();
        abs(2, P_AMRANI, ancre(0)).poser();
        abs(3, P_BELKACEM, ancre(2)).statut("ANNULEE").commentaire("Erreur").poser();
        abs(4, P_BELKACEM, ancre(3)).statut("JUSTIFIEE").motif("MALADIE").poser();
        abs(5, P_CHIKHI, ancre(4)).statut("RATTRAPEE").motif("VOYAGE").rattrapee(ancre(10)).poser();
        abs(6, P_DAHMANI, ancre(6)).statut("NON_JUSTIFIEE").motif("NON_JUSTIFIEE").poser();
        abs(7, P_AMRANI, ancre(7)).poser();
        abs(8, P_CENTRE_B, ancre(1)).centre(CENTRE_B).poser();
        abs(9, P_FANTOME, ancre(5)).declaree().poser();
        seance(P_AMRANI, CENTRE_A, ancre(-1), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, ancre(1), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, ancre(1), "SIGNEE");
        seance(P_BELKACEM, CENTRE_A, ancre(2), "SIGNEE");
        seance(P_CHIKHI, CENTRE_A, ancre(3), "FACTUREE");
        seance(P_DAHMANI, CENTRE_A, ancre(4), "CREE");
        seance(P_DAHMANI, CENTRE_A, ancre(5), "ABSENT");
        seance(P_AMRANI, CENTRE_A, ancre(6), "VALIDEE");
        seance(P_AMRANI, CENTRE_A, ancre(7), "VALIDEE");
        seance(P_CENTRE_B, CENTRE_B, ancre(2), "VALIDEE");
    }

    @Test
    void la_semaine_va_du_dimanche_au_samedi_et_donne_le_meme_resultat_quel_que_soit_le_jour_demande() throws Exception {
        semaineAncree();

        golden("semaine_du_dimanche_ancre", doGet(ABSENCES + "/semaine?date=" + ancre(0), adminA()));
        golden("semaine_du_dimanche_ancre", doGet(ABSENCES + "/semaine?date=" + ancre(3), adminA()));
        golden("semaine_du_dimanche_ancre", doGet(ABSENCES + "/semaine?date=" + ancre(6), adminA()));
    }

    @Test
    void les_semaines_voisines_sont_disjointes_et_la_semaine_du_centre_b_est_isolee() throws Exception {
        semaineAncree();

        golden("semaine_suivante", doGet(ABSENCES + "/semaine?date=" + ancre(7), adminA()));
        golden("semaine_precedente", doGet(ABSENCES + "/semaine?date=" + ancre(-1), adminA()));
        golden("semaine_du_centre_b", doGet(ABSENCES + "/semaine?date=" + ancre(0), adminB()));
        golden("semaine_centre_sans_donnee", doGet(ABSENCES + "/semaine?date=" + ancre(0), as(CENTRE_VIDE, "ADMIN")));
    }

    @Test
    void sans_date_la_semaine_est_celle_du_jour_utc_et_exclut_les_jours_des_autres_semaines() throws Exception {
        abs(1, P_AMRANI, jour(0)).poser();
        abs(2, P_BELKACEM, jour(10)).poser();
        abs(3, P_CHIKHI, jour(-10)).poser();
        seance(P_AMRANI, CENTRE_A, jour(0), "VALIDEE");
        seance(P_BELKACEM, CENTRE_A, jour(10), "VALIDEE");
        JsonNode corps = json(doGet(ABSENCES + "/semaine", adminA()));

        assertEquals(1, corps.path("absences").size());
        assertEquals(1, corps.path("seancesRealisees").size());
        golden("semaine_sans_date_le_jour_utc_seul", doGet(ABSENCES + "/semaine", adminA()));
    }

    @Test
    void une_date_illisible_est_refusee_et_le_centre_de_la_session_est_impose() throws Exception {
        semaineAncree();

        golden("semaine_date_illisible_nouveau_code", doGet(ABSENCES + "/semaine?date=hier", adminA()));
        golden("semaine_autre_centre", doGet(ABSENCES + "/semaine?date=" + ancre(0) + "&centerId=" + CENTRE_B, adminA()));
    }

    @Test
    void la_synthese_compte_les_absences_a_qualifier_et_celles_en_retard_depuis_plus_de_trois_jours() throws Exception {
        abs(1, P_AMRANI, jour(1)).poser();
        abs(2, P_BELKACEM, jour(3)).poser();
        abs(3, P_CHIKHI, jour(4)).poser();
        abs(4, P_DAHMANI, jour(30)).declaree().poser();
        abs(5, P_AMRANI, jour(-1)).poser();
        abs(6, P_AMRANI, jour(20)).statut("JUSTIFIEE").motif("MALADIE").poser();
        abs(7, P_BELKACEM, jour(21)).statut("NON_JUSTIFIEE").motif("NON_JUSTIFIEE").poser();
        abs(8, P_CHIKHI, jour(22)).statut("RATTRAPEE").motif("VOYAGE").rattrapee(jour(15)).poser();
        abs(9, P_DAHMANI, jour(40)).statut("ANNULEE").commentaire("Erreur").poser();
        abs(10, P_FANTOME, jour(50)).poser();
        abs(11, P_CENTRE_B, jour(30)).centre(CENTRE_B).poser();
        abs(12, P_CENTRE_B, jour(2)).centre(CENTRE_B).poser();

        golden("synthese_centre_a", doGet(ABSENCES + "/synthese", adminA()));
        golden("synthese_centre_b", doGet(ABSENCES + "/synthese", adminB()));
        golden("synthese_centre_sans_absence", doGet(ABSENCES + "/synthese", as(CENTRE_VIDE, "ADMIN")));
    }

    @Test
    void la_synthese_a_la_borne_du_retard_ne_compte_que_les_absences_strictement_anterieures_a_j_moins_3() throws Exception {
        Map<String, Object> cas = new LinkedHashMap<>();
        abs(1, P_AMRANI, jour(3)).poser();
        cas.put("seule absence a J-3", json(doGet(ABSENCES + "/synthese", adminA())));
        jdbc.update("DELETE FROM absence_patient WHERE center_id = ?", CENTRE_A);
        abs(2, P_AMRANI, jour(4)).poser();
        cas.put("seule absence a J-4", json(doGet(ABSENCES + "/synthese", adminA())));

        goldenNode("synthese_borne_du_retard", mapper.valueToTree(cas));
    }

    @Test
    void la_synthese_exige_le_centre_de_la_session() throws Exception {
        golden("synthese_autre_centre", doGet(ABSENCES + "/synthese?centerId=" + CENTRE_B, adminA()));
    }
}
