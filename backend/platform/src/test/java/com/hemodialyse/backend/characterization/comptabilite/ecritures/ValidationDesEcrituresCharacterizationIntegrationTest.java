package com.hemodialyse.backend.characterization.comptabilite.ecritures;

import com.hemodialyse.backend.characterization.comptabilite.GenerationDEcritures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation C3 — validation d'une écriture ({@code POST /api/v1/comptabilite/ecritures/{id}/valider}) :
 * cycle BROUILLON → VALIDEE → EXPORTEE, immutabilité d'une écriture exportée, ids inconnus, isolation de centre,
 * autorisations, et le constat B11 (aucune écriture n'est créée en brouillon par le système).
 */
class ValidationDesEcrituresCharacterizationIntegrationTest extends EcrituresCharacterizationSupport {

    @Override
    protected String theme() {
        return "validation";
    }

    private Resp valider(int n) throws Exception {
        return doPost("/ecritures/" + ecr(n) + "/valider", "", adminA());
    }

    @Test
    void valider_un_brouillon_le_passe_en_validee_sans_toucher_au_numero_aux_dates_ni_aux_lignes() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "BROUILLON", "100.00");
        goldenEcritures("brouillon_avant_validation", CENTRE_A);
        golden("valider_brouillon_200", valider(1));
        goldenEcritures("brouillon_apres_validation", CENTRE_A);
        goldenLignes("brouillon_lignes_apres_validation", CENTRE_A);
        assertEquals("VALIDEE", statutEnBase(1));
    }

    @Test
    void valider_une_ecriture_deja_validee_reussit_sans_rien_changer() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "VALIDEE", "100.00");
        golden("valider_validee_200", valider(1));
        golden("valider_validee_deux_fois_200", valider(1));
        goldenEcritures("validee_apres_deux_validations", CENTRE_A);
    }

    @Test
    void valider_une_ecriture_exportee_est_refusee_et_elle_reste_exportee() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "EXPORTEE", "100.00");
        golden("valider_exportee_422_ecriture_exportee_immuable", valider(1));
        goldenEcritures("exportee_apres_refus", CENTRE_A);
        goldenLignes("exportee_lignes_apres_refus", CENTRE_A);
    }

    @Test
    void la_validation_ne_controle_ni_la_periode_ni_le_journal() throws Exception {
        periode(CENTRE_A, 2026, 3, true, "comptable");
        vente(1, "VE-2026-000001", "2026-03-10", "BROUILLON", "100.00");
        vente(2, "VE-2026-000002", "2026-03-11", "EXPORTEE", "100.00");
        ecriture(3, CENTRE_A, "ZZ", "ZZ-2026-000001", "2026-03-12", "2026-03-12", "BROUILLON", "Journal non enregistre", null,
                null, L.d("411100", "5.00"), L.c("706", "5.00"));
        golden("valider_brouillon_en_periode_cloturee_200", valider(1));
        golden("valider_exportee_en_periode_cloturee_422", valider(2));
        golden("valider_brouillon_d_un_journal_inconnu_200", valider(3));
        goldenEcritures("periode_cloturee_et_journal_inconnu_apres", CENTRE_A);
    }

    @Test
    void une_ecriture_inconnue_ou_d_un_autre_centre_est_introuvable_en_422() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "BROUILLON", "100.00");
        vente(2, CENTRE_B, "VE", "VE-2026-000001", "2026-03-10", "BROUILLON", "100.00");
        golden("valider_id_inconnu_422", doPost("/ecritures/" + ecr(99) + "/valider", "", adminA()));
        golden("valider_ecriture_d_un_autre_centre_422", valider(2));
        assertEquals("BROUILLON", statutEnBase(2));
        golden("valider_id_mal_forme", doPost("/ecritures/pas-un-uuid/valider", "", adminA()));
        golden("valider_id_zero", doPost("/ecritures/00000000-0000-0000-0000-000000000000/valider", "", adminA()));
    }

    @Test
    void le_centre_demande_doit_etre_celui_de_la_session() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "BROUILLON", "100.00");
        golden("valider_centre_autre_demande_403",
                doPost("/ecritures/" + ecr(1) + "/valider?centerId=" + CENTRE_B, "", adminA()));
        assertEquals("BROUILLON", statutEnBase(1));
        golden("valider_centre_propre_demande_200",
                doPost("/ecritures/" + ecr(1) + "/valider?centerId=" + CENTRE_A, "", adminA()));
    }

    @Test
    void un_administrateur_ne_valide_que_dans_son_centre() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "BROUILLON", "100.00");
        vente(2, CENTRE_B, "VE", "VE-2026-000001", "2026-03-10", "BROUILLON", "100.00");
        golden("valider_par_admin_du_centre_b_200", doPost("/ecritures/" + ecr(2) + "/valider", "", adminB()));
        assertEquals("BROUILLON", statutEnBase(1));
        assertEquals("VALIDEE", statutEnBase(2));
    }

    @Test
    void seul_l_administrateur_valide() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "BROUILLON", "100.00");
        String chemin = "/ecritures/" + ecr(1) + "/valider";
        golden("autorisation_secretaire_403", doPost(chemin, "", secretaireA()));
        golden("autorisation_infirmier_403", doPost(chemin, "", as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_medecin_seul_role_scope_403", doPost(chemin, "", as(CENTRE_A, "MEDECIN")));
        golden("autorisation_direction_role_scope_403", doPost(chemin, "", as(CENTRE_A, "DIRECTION")));
        golden("autorisation_superadmin_role_scope_403", doPost(chemin, "", as(CENTRE_A, "SUPERADMIN")));
        golden("autorisation_anonyme_403", send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(java.net.URI.create(BASE + chemin))));
        assertEquals("BROUILLON", statutEnBase(1));
        golden("autorisation_admin_et_secretaire_200", doPost(chemin, "", as(CENTRE_A, "ADMIN", "SECRETAIRE")));
    }

    @Test
    void le_corps_de_la_requete_est_ignore() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "BROUILLON", "100.00");
        vente(2, "VE-2026-000002", "2026-03-10", "BROUILLON", "100.00");
        golden("valider_avec_corps_json_ignore_200", doPost("/ecritures/" + ecr(1) + "/valider",
                json("{'statut':'EXPORTEE','numeroPiece':'XX-1','montant':1}"), adminA()));
        golden("valider_avec_corps_texte_ignore_200", doPostTexte("/ecritures/" + ecr(2) + "/valider", "n'importe quoi", adminA()));
        goldenEcritures("corps_ignore_etat_apres", CENTRE_A);
    }

    @Test
    void une_ecriture_illisible_en_base_ne_se_valide_pas_et_reste_inchangee() throws Exception {
        ecriture(1, CENTRE_A, "VE", "VE-2026-000001", "2026-03-10", "2026-03-10", "BROUILLON", "Déséquilibrée", null, null,
                L.d("411100", "10.00"), L.c("706", "9.99"));
        ecriture(2, CENTRE_A, "VE", "VE-2026-000002", "2026-03-10", "2026-03-10", "BROUILLON", "Une ligne", null, null,
                L.d("411100", "0.00"));
        golden("valider_desequilibree_en_base_422", valider(1));
        golden("valider_une_seule_ligne_en_base_400", valider(2));
        assertEquals("BROUILLON", statutEnBase(1));
        assertEquals("BROUILLON", statutEnBase(2));
    }

    @Test
    void valider_une_piece_saisie_ou_une_ecriture_a_source_conserve_son_origine() throws Exception {
        UUID modele = UUID.fromString("99996300-0000-0000-0003-000000000001");
        ecriture(1, CENTRE_A, "OD", "OD-2026-000001", "2026-03-11", "2026-03-11", "BROUILLON", "Pièce saisie", ecr(1), modele,
                L.d("6", "5.00"), L.c("4", "5.00"));
        golden("valider_piece_saisie_200", valider(1));
        goldenEcritures("piece_saisie_apres_validation", CENTRE_A);
    }

    @Test
    void valider_relit_et_reecrit_les_axes_analytiques_des_lignes_en_normalisant_code_et_libelle() throws Exception {
        ecriture(1, CENTRE_A, "VE", "VE-2026-000001", "2026-03-10", "2026-03-10", "BROUILLON", "Axes", null, null,
                L.d("411100", "40.00"), L.c("706", "10.00"), L.c("708", "10.00"), L.c("709", "10.00"), L.c("710", "10.00"));
        jdbc.update("UPDATE lignes_ecriture SET axes_analytiques = 'CENTRE:Centre A,service:  Hémodialyse  ' WHERE compte_scf = '411100'");
        jdbc.update("UPDATE lignes_ecriture SET axes_analytiques = 'SEUL' WHERE compte_scf = '706'");
        jdbc.update("UPDATE lignes_ecriture SET axes_analytiques = '   ' WHERE compte_scf = '708'");
        jdbc.update("UPDATE lignes_ecriture SET axes_analytiques = 'CODE:avec:deux-points' WHERE compte_scf = '709'");
        goldenLignes("axes_avant_validation", CENTRE_A);
        golden("valider_avec_axes_200", valider(1));
        goldenLignes("axes_apres_validation_normalises", CENTRE_A);
    }

    // ------------------------------------------------------------------ B11 : le système ne crée jamais de brouillon

    private void fixtureStock() {
        UUID simple = UUID.fromString("99996300-0000-0000-0000-0000000000a1");
        UUID propre = UUID.fromString("99996300-0000-0000-0000-0000000000a2");
        for (Object[] a : new Object[][]{{simple, "SER-10", null, null}, {propre, "EPO-4000", "321", "6021"}}) {
            jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, "
                            + "gere_par_lot, active, pmp_courant, compte_stock, compte_charge) "
                            + "VALUES (?, ?, ?, ?, 'u', 0, 0, FALSE, TRUE, 10.00, ?, ?)",
                    a[0], CENTRE_A, a[1], a[1], a[2], a[3]);
        }
        UUID bon = UUID.randomUUID();
        jdbc.update("INSERT INTO bons_reception (id, center_id, reference, date_reception, statut) VALUES (?, ?, ?, ?, ?)",
                bon, CENTRE_A, "BR-2026-0001", LocalDate.of(2026, 9, 3), "VALIDE");
        for (Object[] l : new Object[][]{{simple, "100", "10.00"}, {propre, "4", "2500.00"}}) {
            jdbc.update("INSERT INTO bons_reception_lignes (id, bon_reception_id, article_id, quantite, prix_unitaire) "
                    + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), bon, l[0], new BigDecimal((String) l[1]), new BigDecimal((String) l[2]));
        }
        for (Object[] m : new Object[][]{{simple, "20", "10.00"}, {propre, "1", "2500.00"}}) {
            jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, mouvement_type, quantite, prix_unitaire, "
                            + "pmp_apres, created_at) VALUES (?, ?, ?, 'SORTIE', ?, ?, NULL, ?)", UUID.randomUUID(), CENTRE_A,
                    m[0], new BigDecimal((String) m[1]), new BigDecimal((String) m[2]),
                    Timestamp.from(LocalDate.of(2026, 9, 5).atTime(9, 0).toInstant(ZoneOffset.UTC)));
        }
    }

    @Test
    void aucune_ecriture_generee_ou_saisie_n_est_creee_en_brouillon_donc_valider_ne_change_rien_en_pratique() throws Exception {
        UUID facture = UUID.randomUUID();
        comptabilite.genererEcritureFacturation(new GenerationDEcritures.Facture(CENTRE_A, facture,
                "FACT-1", UUID.randomUUID(), CNAS, new BigDecimal("1000.00"), new BigDecimal("190.00"),
                new BigDecimal("1190.00"), LocalDate.of(2026, 9, 30), "Séances"));
        comptabilite.genererEcritureReglement(new GenerationDEcritures.Reglement(CENTRE_A, facture,
                UUID.randomUUID(), new BigDecimal("500.00"), LocalDate.of(2026, 9, 12), "CAISSE", "Règlement", CNAS));
        fixtureStock();
        assertEquals(200, doPost("/stock/synchroniser", json("{'from':'2026-09-01','to':'2026-09-30'}"), adminA()).status());
        assertEquals(201, doPost("/modeles", json("{'code':'LOYER','libelle':'Loyer','journal':'AC','actif':true,"
                + "'lignes':[{'sens':'DEBIT','compte':'706'},{'sens':'CREDIT','compte':'512'}]}"), adminA()).status());
        UUID modele = jdbc.queryForObject("SELECT id FROM modeles_piece WHERE center_id = ?", UUID.class, CENTRE_A);
        assertEquals(201, doPost("/pieces", json("{'modeleId':'" + modele + "','date':'2026-09-10','libelle':'Loyer',"
                + "'montants':[100,100]}"), adminA()).status());
        goldenEcritures("ecritures_generees_toutes_validees", CENTRE_A);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ecritures_comptables WHERE center_id = ? AND statut = 'BROUILLON'",
                Integer.class, CENTRE_A));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ecritures_comptables WHERE center_id = ? AND statut = 'EXPORTEE'",
                Integer.class, CENTRE_A));
    }
}
