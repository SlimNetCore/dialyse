package com.hemodialyse.backend.characterization.comptabilite.ecritures;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Filet de caractérisation C3 — export comptable ({@code GET /api/v1/comptabilite/ecritures/export}) : contenu exact du
 * fichier CSV Sage 100 (en-tête, séparateur, fin de ligne, dates, montants, lettrage, échappement), effet de marquage
 * VALIDEE → EXPORTEE porté par un {@code GET} (B7), reprise des écritures déjà exportées et des brouillons dans le fichier
 * (B7), date du fichier = date de pièce (B8), paramètre {@code format} ignoré, isolation de centre, autorisations.
 */
class ExportComptableCharacterizationIntegrationTest extends EcrituresCharacterizationSupport {

    private static final String MARS = "from=2026-03-01&to=2026-03-31";

    @Override
    protected String theme() {
        return "export";
    }

    private Resp exporter(String requete) throws Exception {
        return doGet("/ecritures/export?" + requete, adminA());
    }

    /**
     * Facture de mars : débit client payeur (lettrage), crédit ventes et TVA ; trois lignes à identifiants fixes.
     */
    private void factureAvecTva(int n, String numero, String date, String statut) {
        ecriture(n, CENTRE_A, "VE", numero, date, date, statut, "Fact. " + numero, null, null,
                L.d("411500", "1190.00").libelle("Facture " + numero + " - Séances").tiers(CNAS),
                L.c("706", "1000.00").libelle("Prestation dialyse - " + numero),
                L.c("44571", "190.00").libelle("TVA collectée - " + numero));
    }

    // ------------------------------------------------------------------ contenu du fichier

    @Test
    void le_fichier_sage_100_contient_l_en_tete_et_une_ligne_par_ligne_d_ecriture_en_csv_a_point_virgule_et_crlf() throws Exception {
        factureAvecTva(1, "VE-2026-000001", "2026-03-10", "VALIDEE");
        golden_csv_exact("export_une_ecriture_validee_fichier_200", exporter(MARS));
    }

    private void golden_csv_exact(String nom, Resp r) {
        goldenCsv(nom, r);
    }

    @Test
    void la_date_du_fichier_est_la_date_de_piece_et_la_periode_porte_sur_la_date_d_ecriture() throws Exception {
        ecriture(1, CENTRE_A, "VE", "VE-2026-000001", "2026-04-01", "2026-03-31", "VALIDEE", "Pièce du 31 mars", null, null,
                L.d("411100", "10.00"), L.c("706", "10.00"));
        golden_csv_exact("export_periode_avril_date_ecriture_dans_la_periode_date_de_piece_dans_le_fichier_200",
                exporter("from=2026-04-01&to=2026-04-30"));
        assertEquals("EXPORTEE", statutEnBase(1));
    }

    @Test
    void une_ecriture_dont_la_date_d_ecriture_est_hors_periode_n_est_ni_exportee_ni_marquee_meme_si_sa_date_de_piece_est_dans_la_periode() throws Exception {
        ecriture(1, CENTRE_A, "VE", "VE-2026-000001", "2026-04-01", "2026-03-31", "VALIDEE", "Pièce du 31 mars", null, null,
                L.d("411100", "10.00"), L.c("706", "10.00"));
        golden_csv_exact("export_periode_mars_date_de_piece_seule_dans_la_periode_fichier_vide_200", exporter(MARS));
        assertEquals("VALIDEE", statutEnBase(1));
    }

    @Test
    void les_bornes_de_la_periode_sont_incluses() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-01", "VALIDEE", "10.00");
        vente(2, "VE-2026-000002", "2026-03-31", "VALIDEE", "20.00");
        vente(3, "VE-2026-000003", "2026-02-28", "VALIDEE", "30.00");
        vente(4, "VE-2026-000004", "2026-04-01", "VALIDEE", "40.00");
        goldenCsvTrie("export_bornes_incluses_fichier_200", exporter(MARS));
        assertEquals("EXPORTEE", statutEnBase(1));
        assertEquals("EXPORTEE", statutEnBase(2));
        assertEquals("VALIDEE", statutEnBase(3));
        assertEquals("VALIDEE", statutEnBase(4));
    }

    @Test
    void les_montants_sont_formates_avec_virgule_sans_separateur_de_milliers_et_un_montant_nul_est_vide() throws Exception {
        ecriture(1, CENTRE_A, "VE", "VE-2026-000001", "2026-03-10", "2026-03-10", "VALIDEE", "Montants", null, null,
                L.d("411100", "1234567.89"), L.c("706", "1234567.88"), L.c("708", "0.01"));
        ecriture(2, CENTRE_A, "VE", "VE-2026-000002", "2026-03-10", "2026-03-10", "VALIDEE", "Entiers", null, null,
                L.d("411100", "100"), L.c("706", "100.50"), L.d("708", "0.50"));
        goldenCsvTrie("export_montants_formats_fichier_200", exporter(MARS));
    }

    @Test
    void les_libelles_de_ligne_sont_echappes_selon_le_csv_et_le_lettrage_est_le_debut_du_tiers_en_majuscules() throws Exception {
        UUID tiers = UUID.fromString("abcdef12-3456-7890-abcd-ef1234567890");
        ecriture(1, CENTRE_A, "VE", "VE-2026-000001", "2026-03-10", "2026-03-10", "VALIDEE", "Échappements", null, null,
                L.d("411100", "30.00").libelle("Avec ; point-virgule").tiers(tiers),
                L.c("706", "10.00").libelle("Avec \"guillemets\""),
                L.c("708", "10.00").libelle("Avec\nretour ligne"),
                L.c("709", "5.00").libelle("Accentué é à ç œ"),
                L.c("710", "5.00").libelle(""));
        goldenCsv("export_echappements_et_lettrage_fichier_200", exporter(MARS));
        jdbc.update("UPDATE lignes_ecriture SET libelle_ligne = NULL WHERE compte_scf = '710'");
        goldenCsvNouveauCode("export_libelle_de_ligne_nul_en_base_fichier_200", exporter(MARS));
    }

    @Test
    void les_lignes_d_une_ecriture_sont_dans_l_ordre_de_leur_identifiant() throws Exception {
        ecriture(1, CENTRE_A, "VE", "VE-2026-000001", "2026-03-10", "2026-03-10", "VALIDEE", "Ordre des lignes", null, null,
                L.c("706", "10.00"), L.d("411100", "30.00"), L.c("44571", "20.00"));
        goldenCsv("export_ordre_des_lignes_par_identifiant_fichier_200", exporter(MARS));
    }

    // ------------------------------------------------------------------ marquage et reprise

    @Test
    void l_export_marque_les_ecritures_validees_exportees_et_ne_touche_ni_aux_lignes_ni_aux_numeros() throws Exception {
        factureAvecTva(1, "VE-2026-000001", "2026-03-10", "VALIDEE");
        factureAvecTva(2, "VE-2026-000002", "2026-03-11", "VALIDEE");
        goldenEcritures("marquage_avant", CENTRE_A);
        goldenLignes("marquage_lignes_avant", CENTRE_A);
        goldenCsvTrie("marquage_fichier_200", exporter(MARS));
        goldenEcritures("marquage_apres", CENTRE_A);
        goldenLignes("marquage_lignes_apres", CENTRE_A);
    }

    @Test
    void un_deuxieme_export_ne_reprend_pas_les_ecritures_deja_exportees() throws Exception {
        factureAvecTva(1, "VE-2026-000001", "2026-03-10", "VALIDEE");
        Resp premier = exporter(MARS);
        Resp second = exporter(MARS);
        assertNotEquals(premier.raw(), second.raw());
        assertEquals("Journal;Piece;Date;Compte;Libelle;Debit;Credit;Lettrage\r\n", second.raw());
        goldenCsv("export_premier_fichier_200", premier);
        goldenCsvNouveauCode("export_deuxieme_fichier_identique_200", second);
        goldenEcritures("export_deux_fois_etat_final", CENTRE_A);
    }

    @Test
    void le_fichier_reprend_aussi_les_brouillons_et_les_exportees_mais_seules_les_validees_passent_a_exportee() throws Exception {
        factureAvecTva(1, "VE-2026-000001", "2026-03-10", "BROUILLON");
        factureAvecTva(2, "VE-2026-000002", "2026-03-11", "VALIDEE");
        factureAvecTva(3, "VE-2026-000003", "2026-03-12", "EXPORTEE");
        goldenCsvTrieNouveauCode("export_mixte_fichier_200", exporter(MARS));
        goldenEcritures("export_mixte_etat_apres", CENTRE_A);
        assertEquals("BROUILLON", statutEnBase(1));
        assertEquals("EXPORTEE", statutEnBase(2));
        assertEquals("EXPORTEE", statutEnBase(3));
    }

    @Test
    void un_brouillon_exporte_puis_valide_passe_a_exportee_au_export_suivant() throws Exception {
        factureAvecTva(1, "VE-2026-000001", "2026-03-10", "BROUILLON");
        exporter(MARS);
        assertEquals("BROUILLON", statutEnBase(1));
        assertEquals(200, doPost("/ecritures/" + ecr(1) + "/valider", "", adminA()).status());
        goldenCsv("export_apres_validation_du_brouillon_fichier_200", exporter(MARS));
        assertEquals("EXPORTEE", statutEnBase(1));
    }

    @Test
    void une_ecriture_exportee_ne_se_valide_plus() throws Exception {
        factureAvecTva(1, "VE-2026-000001", "2026-03-10", "VALIDEE");
        exporter(MARS);
        golden("valider_apres_export_422_ecriture_exportee_immuable",
                doPost("/ecritures/" + ecr(1) + "/valider", "", adminA()));
        assertEquals("EXPORTEE", statutEnBase(1));
    }

    @Test
    void l_export_ne_marque_que_le_journal_et_la_periode_demandes_dans_le_centre_de_la_session() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        ecriture(2, CENTRE_A, "BQ", "BQ-2026-000001", "2026-03-10", "2026-03-10", "VALIDEE", "Banque", null, null,
                L.d("512", "20.00"), L.c("411100", "20.00"));
        vente(3, CENTRE_B, "VE", "VE-2026-000001", "2026-03-10", "VALIDEE", "30.00");
        vente(4, "VE-2026-000002", "2026-05-10", "VALIDEE", "40.00");
        goldenCsvTrie("export_journal_ve_centre_a_fichier_200", exporter(MARS));
        assertEquals("EXPORTEE", statutEnBase(1));
        assertEquals("VALIDEE", statutEnBase(2));
        assertEquals("VALIDEE", statutEnBase(3));
        assertEquals("VALIDEE", statutEnBase(4));
        goldenCsvTrie("export_journal_bq_fichier_200", exporter(MARS + "&journalCode=BQ"));
        assertEquals("EXPORTEE", statutEnBase(2));
    }

    @Test
    void une_periode_cloturee_s_exporte_et_se_marque_comme_une_autre() throws Exception {
        periode(CENTRE_A, 2026, 3, true, "comptable");
        factureAvecTva(1, "VE-2026-000001", "2026-03-10", "VALIDEE");
        goldenCsv("export_periode_cloturee_fichier_200", exporter(MARS));
        assertEquals("EXPORTEE", statutEnBase(1));
    }

    @Test
    void tout_le_resultat_est_exporte_sans_pagination() throws Exception {
        for (int i = 1; i <= 60; i++) {
            vente(i, String.format("VE-2026-%06d", i), "2026-03-10", "VALIDEE", "10.00");
        }
        Resp r = exporter(MARS);
        assertEquals(200, r.status());
        assertEquals(1 + 60 * 2, r.raw().split("\r\n").length);
        assertEquals(60, jdbc.queryForObject("SELECT COUNT(*) FROM ecritures_comptables WHERE center_id = ? AND statut = 'EXPORTEE'",
                Integer.class, CENTRE_A));
    }

    // ------------------------------------------------------------------ paramètres : journal, format, dates

    @Test
    void le_journal_par_defaut_est_ve_et_son_code_est_normalise_en_majuscules() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        ecriture(2, CENTRE_A, "BQ", "BQ-2026-000001", "2026-03-10", "2026-03-10", "VALIDEE", "Banque", null, null,
                L.d("512", "20.00"), L.c("411100", "20.00"));
        golden_csv_exact("journal_par_defaut_ve_nom_du_fichier_200", exporter(MARS));
        assertEquals("VALIDEE", statutEnBase(2));
        golden_csv_exact("journal_minuscules_normalise_nom_du_fichier_en_majuscules_200", exporter(MARS + "&journalCode=bq"));
        goldenCsvNouveauCode("journal_avec_espaces_rogne_200", exporter(MARS + "&journalCode=%20BQ%20"));
    }

    @Test
    void un_journal_sans_ecriture_donne_un_fichier_avec_seulement_l_en_tete() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        golden_csv_exact("journal_inconnu_fichier_vide_200", exporter(MARS + "&journalCode=ZZ"));
        assertEquals("VALIDEE", statutEnBase(1));
    }

    @Test
    void un_journal_blanc_ou_hors_format_est_refuse_en_400_sans_rien_marquer() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        golden("journal_blanc_400", exporter(MARS + "&journalCode=%20"));
        golden("journal_avec_tiret_400", exporter(MARS + "&journalCode=V-E"));
        golden("journal_onze_caracteres_400", exporter(MARS + "&journalCode=ABCDEFGHIJK"));
        assertEquals("VALIDEE", statutEnBase(1));
    }

    @Test
    void un_journal_vide_dans_l_url_prend_la_valeur_par_defaut_ve_comme_un_journal_absent() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        ecriture(2, CENTRE_A, "BQ", "BQ-2026-000001", "2026-03-10", "2026-03-10", "VALIDEE", "Banque", null, null,
                L.d("512", "20.00"), L.c("411100", "20.00"));
        golden_csv_exact("journal_vide_prend_ve_par_defaut_200", exporter(MARS + "&journalCode="));
        assertEquals("EXPORTEE", statutEnBase(1));
        assertEquals("VALIDEE", statutEnBase(2));
    }

    @Test
    void le_parametre_format_est_ignore_et_le_fichier_est_toujours_du_csv_sage_100() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        Resp sage = exporter(MARS + "&format=SAGE100");
        cleanupEcritures();
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        Resp inconnu = exporter(MARS + "&format=XML");
        cleanupEcritures();
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        Resp vide = exporter(MARS + "&format=");
        cleanupEcritures();
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        Resp absent = exporter(MARS);
        assertEquals(sage.raw(), inconnu.raw());
        assertEquals(sage.raw(), vide.raw());
        assertEquals(sage.raw(), absent.raw());
        assertEquals(200, inconnu.status());
        assertEquals(sage.contentType(), inconnu.contentType());
        goldenCsv("format_xml_ignore_fichier_sage_100_200", inconnu);
    }

    @Test
    void les_dates_sont_obligatoires_et_valides_et_une_periode_inversee_donne_un_fichier_vide() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        golden("sans_from", doGet("/ecritures/export?to=2026-03-31", adminA()));
        golden("sans_to", doGet("/ecritures/export?from=2026-03-01", adminA()));
        golden("from_mal_forme", exporter("from=01-03-2026&to=2026-03-31"));
        golden("to_inexistant_30_fevrier", exporter("from=2026-03-01&to=2026-02-30"));
        golden_csv_exact("periode_inversee_fichier_vide_200", exporter("from=2026-03-31&to=2026-03-01"));
        assertEquals("VALIDEE", statutEnBase(1));
    }

    // ------------------------------------------------------------------ centre, rôles

    @Test
    void le_centre_demande_doit_etre_celui_de_la_session_et_un_refus_ne_marque_rien() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        golden("centre_autre_demande_403", exporter(MARS + "&centerId=" + CENTRE_B));
        assertEquals("VALIDEE", statutEnBase(1));
        golden_csv_exact("centre_propre_demande_200", exporter(MARS + "&centerId=" + CENTRE_A));
        golden("centre_illisible", exporter(MARS + "&centerId=pas-un-uuid"));
    }

    @Test
    void un_centre_exporte_ses_ecritures_et_pas_celles_d_un_autre_centre() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        vente(2, CENTRE_B, "VE", "VE-2026-000001", "2026-03-10", "VALIDEE", "20.00");
        golden_csv_exact("centre_b_exporte_les_siennes_200", doGet("/ecritures/export?" + MARS, adminB()));
        assertEquals("VALIDEE", statutEnBase(1));
        assertEquals("EXPORTEE", statutEnBase(2));
        golden_csv_exact("centre_sans_ecriture_fichier_vide_200", doGet("/ecritures/export?" + MARS, as(CENTRE_VIDE, "ADMIN")));
    }

    @Test
    void seul_l_administrateur_exporte_et_un_refus_ne_marque_rien() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        String chemin = "/ecritures/export?" + MARS;
        golden("autorisation_secretaire_403", doGet(chemin, secretaireA()));
        golden("autorisation_infirmier_403", doGet(chemin, as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_medecin_seul_role_scope_403", doGet(chemin, as(CENTRE_A, "MEDECIN")));
        golden("autorisation_direction_role_scope_403", doGet(chemin, as(CENTRE_A, "DIRECTION")));
        golden("autorisation_superadmin_role_scope_403", doGet(chemin, as(CENTRE_A, "SUPERADMIN")));
        golden("autorisation_anonyme_403", doGetAnonymous(chemin));
        assertEquals("VALIDEE", statutEnBase(1));
        golden_csv_exact("autorisation_admin_200", doGet(chemin, adminA()));
    }

    // ------------------------------------------------------------------ données illisibles

    @Test
    void une_ecriture_illisible_dans_la_selection_fait_echouer_tout_l_export_sans_rien_marquer() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        ecriture(2, CENTRE_A, "VE", "VE-2026-000002", "2026-03-11", "2026-03-11", "VALIDEE", "Déséquilibrée", null, null,
                L.d("411100", "10.00"), L.c("706", "9.99"));
        golden("export_desequilibre_en_base_422", exporter(MARS));
        assertEquals("VALIDEE", statutEnBase(1));
        assertEquals("VALIDEE", statutEnBase(2));
    }

    @Test
    void un_statut_inconnu_en_base_fait_echouer_l_export_en_400() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "10.00");
        vente(2, "VE-2026-000002", "2026-03-11", "ANNULEE", "10.00");
        golden("export_statut_inconnu_en_base_400", exporter(MARS));
        assertEquals("VALIDEE", statutEnBase(1));
    }
}
