package com.hemodialyse.backend.characterization.comptabilite.ecritures;

import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Filet de caractérisation C3 — parcours complet du mois comptable d'un centre : génération (ventes et règlements par les
 * cas d'usage de production) → consultation → validation → export → consultation des statuts → clôture → refus de toute
 * nouvelle écriture sur le mois clos → réexport du mois clos. Un seul centre, un seul mois (mars 2026), puis avril.
 */
class ParcoursDesEcrituresCharacterizationIntegrationTest extends EcrituresCharacterizationSupport {

    private static final String MARS = "from=2026-03-01&to=2026-03-31";

    @Override
    protected String theme() {
        return "parcours";
    }

    @Test
    void parcours_du_mois_de_mars_de_la_generation_des_ecritures_a_la_cloture_et_au_refus_de_toute_nouvelle_ecriture() throws Exception {
        // 1. génération (C5) : deux factures du payeur CNAS, un règlement bancaire, un règlement en caisse
        genererVente(CENTRE_A, fid(1), "FACT-1", "1000.00", "190.00", "1190.00", "2026-03-05", CNAS);
        genererVente(CENTRE_A, fid(2), "FACT-2", "2000.00", "380.00", "2380.00", "2026-03-20", CNAS);
        genererReglement(CENTRE_A, fid(1), rid(1), "1190.00", "2026-03-25", "BANQUE", CNAS);
        genererReglement(CENTRE_A, fid(2), rid(2), "500.00", "2026-03-26", "CAISSE", CNAS);
        goldenEcritures("etape_1_apres_generation", CENTRE_A);

        // 2. consultation du mois : quatre écritures validées, du plus récent au plus ancien
        golden("etape_2_consultation_du_mois_200", doGet("/ecritures?" + MARS, adminA()));
        golden("etape_2_consultation_des_brouillons_vide_200", doGet("/ecritures?" + MARS + "&statut=BROUILLON", adminA()));

        // 3. validation : sans effet puisque tout est déjà validé (B11)
        Resp validee = doGet("/ecritures?" + MARS + "&journalCode=VE&size=1", adminA());
        String id = json(validee).path("items").get(0).path("id").asString();
        Resp valider = doPost("/ecritures/" + id + "/valider", "", adminA());
        assertEquals(200, valider.status());
        golden("etape_3_validation_d_une_ecriture_deja_validee_200", valider);

        // 4. export du journal des ventes : fichier puis statuts
        goldenCsvTrie("etape_4_export_ventes_fichier_200", doGet("/ecritures/export?" + MARS, adminA()));
        golden("etape_4_statuts_apres_export_ventes_200", doGet("/ecritures?" + MARS + "&size=10", adminA()));

        // 5. un deuxième export des ventes reprend les mêmes écritures déjà exportées
        goldenCsvTrie("etape_5_reexport_ventes_fichier_identique_200", doGet("/ecritures/export?" + MARS, adminA()));

        // 6. export des journaux de banque et de caisse
        goldenCsvTrie("etape_6_export_banque_fichier_200", doGet("/ecritures/export?" + MARS + "&journalCode=BQ", adminA()));
        goldenCsvTrie("etape_6_export_caisse_fichier_200", doGet("/ecritures/export?" + MARS + "&journalCode=CA", adminA()));
        golden("etape_6_exportees_200", doGet("/ecritures?" + MARS + "&statut=EXPORTEE&size=10", adminA()));

        // 7. une écriture exportée ne se valide plus
        golden("etape_7_validation_d_une_ecriture_exportee_422", doPost("/ecritures/" + id + "/valider", "", adminA()));

        // 8. clôture de mars
        golden("etape_8_cloture_de_mars_200", doPost("/periodes/cloturer",
                json("{'annee':2026,'mois':3,'userId':'comptable'}"), adminA()));
        goldenPeriodes("etape_8_periodes", CENTRE_A);
        golden("etape_8_deuxieme_cloture_422", doPost("/periodes/cloturer",
                json("{'annee':2026,'mois':3,'userId':'comptable'}"), adminA()));

        // 9. plus aucune écriture sur mars ; avril reste ouvert et reprend la numérotation de son mois
        assertEquals("PERIODE_CLOTUREE", assertThrows(BusinessException.class,
                () -> genererVente(CENTRE_A, fid(3), "FACT-3", "100.00", "0.00", "100.00", "2026-03-31", CNAS)).getCode());
        assertEquals("PERIODE_CLOTUREE", assertThrows(BusinessException.class,
                () -> genererReglement(CENTRE_A, fid(1), rid(3), "10.00", "2026-03-30", "BANQUE", CNAS)).getCode());
        genererVente(CENTRE_A, fid(4), "FACT-4", "100.00", "19.00", "119.00", "2026-04-01", CNAS);
        genererReglement(CENTRE_A, fid(4), rid(4), "119.00", "2026-04-02", "BANQUE", CNAS);
        goldenEcritures("etape_9_apres_cloture_et_nouvelles_ecritures_d_avril", CENTRE_A);

        // 10. le mois clos s'exporte encore à l'identique ; avril s'exporte en son propre fichier
        goldenCsvTrie("etape_10_reexport_du_mois_clos_fichier_200", doGet("/ecritures/export?" + MARS, adminA()));
        goldenCsvTrie("etape_10_export_d_avril_fichier_200",
                doGet("/ecritures/export?from=2026-04-01&to=2026-04-30", adminA()));
        golden("etape_10_etat_final_des_statuts_200", doGet("/ecritures?from=2026-03-01&to=2026-04-30&size=20", adminA()));
    }

    @Test
    void parcours_d_un_second_centre_independant_pendant_que_le_premier_cloture() throws Exception {
        genererVente(CENTRE_A, fid(1), "FACT-A1", "100.00", "0.00", "100.00", "2026-03-05", null);
        genererVente(CENTRE_B, fid(11), "FACT-B1", "200.00", "0.00", "200.00", "2026-03-05", null);
        assertEquals(200, doPost("/periodes/cloturer", json("{'annee':2026,'mois':3,'userId':'a'}"), adminA()).status());
        genererVente(CENTRE_B, fid(12), "FACT-B2", "300.00", "0.00", "300.00", "2026-03-06", null);
        goldenCsvTrie("centre_b_export_200", doGet("/ecritures/export?" + MARS, adminB()));
        golden("centre_a_consultation_200", doGet("/ecritures?" + MARS, adminA()));
        golden("centre_b_consultation_200", doGet("/ecritures?" + MARS, adminB()));
        goldenPeriodes("centre_a_periodes", CENTRE_A);
        goldenPeriodes("centre_b_periodes", CENTRE_B);
    }
}
