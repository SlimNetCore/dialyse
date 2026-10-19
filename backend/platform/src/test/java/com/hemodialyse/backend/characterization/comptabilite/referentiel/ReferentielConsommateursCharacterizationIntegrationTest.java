package com.hemodialyse.backend.characterization.comptabilite.referentiel;

import com.hemodialyse.backend.characterization.comptabilite.GenerationDEcritures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation C1 — lectures du référentiel (plan comptable, journaux, compte client des payeurs) par les
 * autres routes et services de la Comptabilité : mapping (C2), modèles de pièces et pièces (C4), écritures de ventes
 * (C5). Ces scénarios verrouillent ce que C1 DOIT continuer à fournir à ses consommateurs (plan SCF de départ,
 * journaux par défaut, compte client par défaut — RG-CPT-018, RG-CPT-019). Les routes consommatrices ne sont que des
 * points d'entrée ; leur propre comportement relève de leurs slices.
 */
class ReferentielConsommateursCharacterizationIntegrationTest extends ReferentielCharacterizationSupport {

    private static final String MAPPING_OK = "{'compteVentes':'706','compteClientPatient':'411100',"
            + "'compteClientDefaut':'411500','compteBanque':'512','compteCaisse':'530','compteTVACollectee':'44571'";
    @Autowired
    private GenerationDEcritures comptabilite;

    private static String mapping(String ventes, String journaux) {
        return json("{'compteVentes':'" + ventes + "','compteClientPatient':'411100','compteClientDefaut':'411500',"
                + "'compteBanque':'512','compteCaisse':'530','compteTVACollectee':'44571'"
                + (journaux == null ? "" : ",'journaux':" + journaux) + "}");
    }

    private static String modele(String journal, String compteDebit) {
        return json("{'code':'loyer','libelle':'Loyer','journal':'" + journal + "','actif':true,'lignes':["
                + "{'sens':'DEBIT','compte':'" + compteDebit + "'},{'sens':'CREDIT','compte':'401'}]}");
    }

    @Override
    protected String theme() {
        return "consommateurs";
    }

    private void facture(UUID payeur, String numero) {
        comptabilite.genererEcritureFacturation(new GenerationDEcritures.Facture(CENTRE_A,
                UUID.randomUUID(), numero, UUID.randomUUID(), payeur, new BigDecimal("1000.00"), BigDecimal.ZERO,
                new BigDecimal("1000.00"), LocalDate.of(2026, 10, 5), "Séances"));
    }

    private void goldenLignes(String name) {
        goldenSql(name, "SELECT e.journal_code, e.numero_piece, l.compte_scf, l.montant_debit, l.montant_credit "
                + "FROM lignes_ecriture l JOIN ecritures_comptables e ON e.id = l.ecriture_id "
                + "WHERE e.center_id = '" + CENTRE_A + "' ORDER BY e.numero_piece, l.compte_scf");
    }

    // ------------------------------------------------------------------ mapping (C2) : plan et journaux exigés

    @Test
    void le_mapping_n_accepte_que_des_comptes_actifs_du_plan_de_depart_quand_le_centre_n_a_rien_enregistre() throws Exception {
        golden("mapping_plan_de_depart_accepte_200", doPut("/mapping", mapping("706", null), adminA()));
        golden("mapping_compte_hors_plan_422", doPut("/mapping", mapping("7099", null), adminA()));
        assertEquals(0, count("comptes_comptables", CENTRE_A));
    }

    @Test
    void le_mapping_refuse_un_compte_desactive_du_plan_enregistre_et_un_compte_de_casse_differente() throws Exception {
        compte(CENTRE_A, "706", "Prestations", true);
        for (String c : new String[]{"411100", "411500", "512", "530", "44571", "322", "602", "408", "757", "657"}) {
            compte(CENTRE_A, c, "Compte " + c, true);
        }
        compte(CENTRE_A, "7061", "Prestations désactivées", false);
        compte(CENTRE_A, "ABC", "Majuscules", true);
        golden("mapping_plan_enregistre_accepte_200", doPut("/mapping", mapping("706", null), adminA()));
        golden("mapping_compte_desactive_422", doPut("/mapping", mapping("7061", null), adminA()));
        golden("mapping_compte_casse_differente_422", doPut("/mapping", mapping("abc", null), adminA()));
        golden("mapping_compte_du_plan_de_depart_absent_du_plan_enregistre_422", doPut("/mapping", mapping("613", null), adminA()));
    }

    @Test
    void le_mapping_exige_des_journaux_actifs_les_journaux_par_defaut_valant_tant_que_le_centre_n_en_a_pas_enregistre() throws Exception {
        golden("mapping_journal_par_defaut_accepte_200", doPut("/mapping", mapping("706", "{'VENTE':'BQ'}"), adminA()));
        golden("mapping_journal_inconnu_422", doPut("/mapping", mapping("706", "{'VENTE':'ZZ'}"), adminA()));
        journal(CENTRE_A, "VE", "Ventes", true);
        journal(CENTRE_A, "BQ", "Banque", true);
        journal(CENTRE_A, "CA", "Caisse", true);
        journal(CENTRE_A, "AC", "Achats", true);
        journal(CENTRE_A, "ST", "Stocks", true);
        journal(CENTRE_A, "OD", "Désactivé", false);
        golden("mapping_journal_desactive_422", doPut("/mapping", mapping("706", "{'STOCK_SORTIE':'OD'}"), adminA()));
        golden("mapping_journal_et_compte_invalides_dit_journal_inconnu_d_abord", doPut("/mapping", mapping("7099", "{'VENTE':'ZZ'}"), adminA()));
        golden("mapping_journal_en_minuscules_normalise_200", doPut("/mapping", mapping("706", "{'VENTE':'bq'}"), adminA()));
    }

    @Test
    void un_compte_du_parametrage_herite_est_protege_des_la_premiere_lecture_du_plan_de_depart() throws Exception {
        mapping(CENTRE_A, "7061", "4111", "411500", "512", "530", "44571");
        golden("compte_du_parametrage_herite_ne_se_desactive_pas_422", doPut("/comptes/7061",
                json("{'libelle':'Compte 7061','actif':false}"), adminA()));
        assertEquals(0, count("comptes_comptables", CENTRE_A));
        golden("compte_du_plan_de_depart_remplace_par_le_parametrage_herite_se_desactive_200", doPut("/comptes/706",
                json("{'libelle':'Prestations de services','actif':false}"), adminA()));
        goldenComptes("parametrage_herite_etat_base", CENTRE_A);
    }

    // ------------------------------------------------------------------ modèles de pièces (C4) et pièces

    @Test
    void un_modele_de_piece_exige_des_comptes_du_plan_et_un_journal_actif() throws Exception {
        golden("modele_nominal_201", doPost("/modeles", modele("AC", "613"), adminA()));
        golden("modele_journal_od_inexistant_422", doPost("/modeles", modele("OD", "613").replace("loyer", "autre"), adminA()));
        golden("modele_journal_inconnu_422", doPost("/modeles", modele("ZZ", "613").replace("loyer", "autre2"), adminA()));
        golden("modele_journal_et_compte_invalides_dit_journal_d_abord", doPost("/modeles", modele("ZZ", "9999").replace("loyer", "autre3"), adminA()));
        golden("modele_compte_hors_plan_dit_compte_inconnu", doPost("/modeles", modele("AC", "9999").replace("loyer", "autre4"), adminA()));
        assertEquals(1, count("modeles_piece", CENTRE_A));
    }

    @Test
    void la_saisie_d_une_piece_relit_le_plan_et_les_journaux_du_centre_au_moment_de_la_saisie() throws Exception {
        assertEquals(201, doPost("/modeles", modele("AC", "613"), adminA()).status());
        String id = jdbc.queryForObject("SELECT CAST(id AS VARCHAR(36)) FROM modeles_piece WHERE center_id = ? AND code = 'LOYER'",
                String.class, CENTRE_A);
        String piece = json("{'modeleId':'" + id + "','date':'2026-10-05','libelle':'Loyer','montants':[100,100]}");
        // plan enregistré après coup, sans le compte 613 : la saisie est refusée
        for (String c : new String[]{"401", "706"}) {
            compte(CENTRE_A, c, "Compte " + c, true);
        }
        golden("saisie_compte_du_modele_absent_du_plan_422", doPost("/pieces", piece, adminA()));
        compte(CENTRE_A, "613", "Locations", false);
        golden("saisie_compte_du_modele_desactive_422", doPost("/pieces", piece, adminA()));
        jdbc.update("UPDATE comptes_comptables SET actif = TRUE WHERE center_id = ? AND numero = '613'", CENTRE_A);
        journal(CENTRE_A, "AC", "Achats", false);
        golden("saisie_journal_du_modele_desactive_422", doPost("/pieces", piece, adminA()));
        jdbc.update("UPDATE journaux_comptables SET actif = TRUE WHERE center_id = ? AND code = 'AC'", CENTRE_A);
        golden("saisie_plan_et_journal_actifs_201", doPost("/pieces", piece, adminA()));
        goldenLignes("saisie_lignes_ecrites");
    }

    // ------------------------------------------------------------------ écritures de ventes (C5) : compte client (RG-CPT-019)

    @Test
    void la_facture_est_debitee_sur_le_compte_du_payeur_sinon_le_compte_client_par_defaut_sinon_le_compte_des_patients() throws Exception {
        assertEquals(200, doPut("/comptes/411210", json("{'libelle':'Clients CNAS','actif':true}"), adminA()).status());
        assertEquals(200, doPut("/payeurs/" + CNAS + "/compte", json("{'compte':'411210'}"), adminA()).status());
        facture(CNAS, "FACT-CNAS");
        facture(MUTUELLE, "FACT-MUT");
        facture(null, "FACT-PATIENT");
        goldenLignes("facture_compte_client_du_payeur_defaut_patient");
    }

    @Test
    void retirer_le_compte_d_un_payeur_lui_rend_le_compte_client_par_defaut_pour_les_factures_suivantes() throws Exception {
        assertEquals(200, doPut("/comptes/411210", json("{'libelle':'Clients CNAS','actif':true}"), adminA()).status());
        assertEquals(200, doPut("/payeurs/" + CNAS + "/compte", json("{'compte':'411210'}"), adminA()).status());
        facture(CNAS, "FACT-AVANT");
        assertEquals(200, doPut("/payeurs/" + CNAS + "/compte", json("{'compte':''}"), adminA()).status());
        facture(CNAS, "FACT-APRES");
        goldenLignes("facture_apres_retrait_du_compte_du_payeur");
    }

    @Test
    void le_compte_du_payeur_est_lu_tel_quel_sans_controle_du_plan_a_la_generation_de_l_ecriture() throws Exception {
        comptePayeur(CENTRE_A, CNAS, "999999");
        facture(CNAS, "FACT-HORS-PLAN");
        goldenLignes("facture_compte_du_payeur_hors_plan_lu_tel_quel");
    }

    @Test
    void le_journal_des_ventes_de_l_ecriture_est_celui_choisi_dans_le_mapping() throws Exception {
        assertEquals(200, doPut("/journaux/OD", json("{'libelle':'Opérations diverses','actif':true}"), adminA()).status());
        assertEquals(200, doPut("/mapping", mapping("706", "{'VENTE':'OD'}"), adminA()).status());
        facture(null, "FACT-OD");
        goldenLignes("facture_journal_des_ventes_choisi_dans_le_mapping");
    }

    @Test
    void un_compte_qui_porte_des_ecritures_de_ventes_se_desactive_mais_ne_se_supprime_pas() throws Exception {
        // le mapping des ventes pointe vers 7061 puis revient à 706 : 7061 porte alors des écritures sans être utilisé
        assertEquals(200, doPut("/comptes/7061", json("{'libelle':'Ventes bis','actif':true}"), adminA()).status());
        assertEquals(200, doPut("/mapping", mapping("7061", null), adminA()).status());
        facture(null, "FACT-7061");
        assertEquals(200, doPut("/mapping", mapping("706", null), adminA()).status());
        golden("compte_des_ventes_passe_supprimer_422_ecritures", doDelete("/comptes/7061", adminA()));
        golden("compte_des_ventes_passe_desactiver_200", doPut("/comptes/7061", json("{'libelle':'Ventes bis','actif':false}"), adminA()));
        goldenLignes("ecritures_de_ventes_conservees");
    }
}
