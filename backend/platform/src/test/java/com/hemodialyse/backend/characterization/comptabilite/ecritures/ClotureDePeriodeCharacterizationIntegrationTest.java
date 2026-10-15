package com.hemodialyse.backend.characterization.comptabilite.ecritures;

import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteUseCase;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Filet de caractérisation C3 — clôture d'une période comptable ({@code POST /api/v1/comptabilite/periodes/cloturer}) :
 * une seule fois, sans réouverture, ADMIN seul, auteur pris dans le corps (B12), et effet sur les écritures (plus aucune
 * génération ni saisie sur le mois clos ; validation et export des écritures existantes toujours possibles).
 */
class ClotureDePeriodeCharacterizationIntegrationTest extends EcrituresCharacterizationSupport {

    @Override
    protected String theme() {
        return "cloture";
    }

    private Resp cloturer(String corps) throws Exception {
        return doPost("/periodes/cloturer", json(corps), adminA());
    }

    private Resp cloturer(int annee, int mois) throws Exception {
        return cloturer("{'annee':" + annee + ",'mois':" + mois + ",'userId':'comptable'}");
    }

    // ------------------------------------------------------------------ clôture

    @Test
    void cloturer_un_mois_l_enregistre_clos_avec_son_auteur_et_repond_closed_true_et_la_periode_sans_zero_de_tete() throws Exception {
        golden("cloturer_mars_2026_200", cloturer(2026, 3));
        goldenPeriodes("cloturer_mars_2026_etat", CENTRE_A);
        golden("cloturer_octobre_2026_200", cloturer(2026, 10));
        golden("cloturer_decembre_2026_200", cloturer(2026, 12));
        goldenPeriodes("cloturer_trois_mois_etat", CENTRE_A);
    }

    @Test
    void cloturer_deux_fois_le_meme_mois_est_refuse_et_ne_change_pas_la_premiere_cloture() throws Exception {
        assertEquals(200, cloturer("{'annee':2026,'mois':3,'userId':'premier'}").status());
        golden("recloturer_422_periode_deja_cloturee", cloturer("{'annee':2026,'mois':3,'userId':'second'}"));
        goldenPeriodes("recloturer_etat_inchange", CENTRE_A);
    }

    @Test
    void une_periode_future_ancienne_ou_sans_ecriture_se_cloture_sans_controle() throws Exception {
        golden("cloturer_mois_futur_2030_200", cloturer(2030, 12));
        golden("cloturer_mois_ancien_2000_200", cloturer(2000, 1));
        golden("cloturer_mois_sans_ecriture_2026_07_200", cloturer(2026, 7));
        goldenPeriodes("cloturer_futur_ancien_etat", CENTRE_A);
    }

    @Test
    void les_mois_se_cloturent_independamment_les_uns_des_autres_et_d_un_centre_a_l_autre() throws Exception {
        assertEquals(200, cloturer(2026, 3).status());
        golden("cloturer_avril_apres_mars_200", cloturer(2026, 4));
        golden("cloturer_mars_2025_meme_mois_autre_annee_200", cloturer(2025, 3));
        golden("cloturer_mars_2026_par_le_centre_b_200",
                doPost("/periodes/cloturer", json("{'annee':2026,'mois':3,'userId':'comptable-b'}"), adminB()));
        goldenPeriodes("independance_centre_a", CENTRE_A);
        goldenPeriodes("independance_centre_b", CENTRE_B);
    }

    @Test
    void une_ligne_de_periode_existante_mais_non_cloturee_est_mise_a_jour_et_non_dupliquee() throws Exception {
        periode(CENTRE_A, 2026, 3, false, null);
        golden("cloturer_ligne_ouverte_existante_200", cloturer(2026, 3));
        goldenPeriodes("cloturer_ligne_ouverte_existante_etat", CENTRE_A);
        assertEquals(1, count("periodes_comptables", CENTRE_A));
        assertNotNull(jdbc.queryForObject("SELECT cloturee_at FROM periodes_comptables WHERE center_id = ?",
                Timestamp.class, CENTRE_A));
    }

    @Test
    void une_periode_cloturee_en_base_est_refusee_meme_si_son_auteur_est_vide() throws Exception {
        periode(CENTRE_A, 2026, 3, true, null);
        golden("recloturer_ligne_heritee_sans_auteur_422", cloturer(2026, 3));
        goldenPeriodes("recloturer_ligne_heritee_etat", CENTRE_A);
    }

    // ------------------------------------------------------------------ auteur, centre

    @Test
    void l_auteur_de_la_cloture_est_le_champ_userId_du_corps_et_non_l_utilisateur_connecte_et_n_est_pas_rogne() throws Exception {
        golden("auteur_libre_du_corps_200", cloturer("{'annee':2026,'mois':3,'userId':'quelqu-un-d-autre'}"));
        golden("auteur_avec_espaces_conserves_200", cloturer("{'annee':2026,'mois':4,'userId':'  bob  '}"));
        golden("auteur_120_caracteres_200", cloturer("{'annee':2026,'mois':5,'userId':'" + "a".repeat(120) + "'}"));
        goldenPeriodes("auteur_etat", CENTRE_A);
        golden("auteur_121_caracteres_refuse_par_la_base_500", cloturer("{'annee':2026,'mois':6,'userId':'" + "a".repeat(121) + "'}"));
        assertEquals(3, count("periodes_comptables", CENTRE_A));
    }

    @Test
    void le_centre_du_corps_doit_etre_celui_de_la_session_ou_absent() throws Exception {
        golden("centre_autre_dans_le_corps_403",
                doPost("/periodes/cloturer", json("{'centerId':'" + CENTRE_B + "','annee':2026,'mois':3,'userId':'u'}"), adminA()));
        assertEquals(0, count("periodes_comptables", CENTRE_B));
        assertEquals(0, count("periodes_comptables", CENTRE_A));
        golden("centre_propre_dans_le_corps_200",
                doPost("/periodes/cloturer", json("{'centerId':'" + CENTRE_A + "','annee':2026,'mois':3,'userId':'u'}"), adminA()));
        golden("centre_nul_dans_le_corps_session_200",
                doPost("/periodes/cloturer", json("{'centerId':null,'annee':2026,'mois':4,'userId':'u'}"), adminA()));
        golden("centre_illisible_dans_le_corps",
                doPost("/periodes/cloturer", json("{'centerId':'pas-un-uuid','annee':2026,'mois':5,'userId':'u'}"), adminA()));
        golden("centre_en_parametre_de_requete_ignore_200",
                doPost("/periodes/cloturer?centerId=" + CENTRE_B, json("{'annee':2026,'mois':6,'userId':'u'}"), adminA()));
        goldenPeriodes("centre_etat_a", CENTRE_A);
        assertEquals(0, count("periodes_comptables", CENTRE_B));
    }

    // ------------------------------------------------------------------ validation du corps

    @Test
    void l_annee_est_bornee_de_2000_a_3000_et_le_mois_de_1_a_12() throws Exception {
        golden("annee_1999_400", cloturer(1999, 3));
        golden("annee_3001_400", cloturer(3001, 3));
        golden("mois_0_400", cloturer(2026, 0));
        golden("mois_13_400", cloturer(2026, 13));
        golden("mois_negatif_400", cloturer(2026, -1));
        assertEquals(0, count("periodes_comptables", CENTRE_A));
        golden("annee_2000_mois_1_200", cloturer(2000, 1));
        golden("annee_3000_mois_12_200", cloturer(3000, 12));
        goldenPeriodes("bornes_etat", CENTRE_A);
    }

    @Test
    void l_auteur_est_obligatoire_et_non_blanc() throws Exception {
        golden("auteur_absent_400", cloturer("{'annee':2026,'mois':3}"));
        golden("auteur_nul_400", cloturer("{'annee':2026,'mois':3,'userId':null}"));
        golden("auteur_vide_400", cloturer("{'annee':2026,'mois':3,'userId':''}"));
        golden("auteur_blanc_400", cloturer("{'annee':2026,'mois':3,'userId':'   '}"));
        golden("auteur_nombre_coerce_200", cloturer("{'annee':2026,'mois':3,'userId':42}"));
        goldenPeriodes("auteur_validation_etat", CENTRE_A);
    }

    @Test
    void les_champs_manquants_ou_de_mauvais_type_et_les_corps_illisibles_sont_refuses() throws Exception {
        golden("corps_vide_objet_500", cloturer("{}"));
        golden("annee_absente_500", cloturer("{'mois':3,'userId':'u'}"));
        golden("mois_absent_500", cloturer("{'annee':2026,'userId':'u'}"));
        golden("annee_nulle", cloturer("{'annee':null,'mois':3,'userId':'u'}"));
        golden("mois_nul", cloturer("{'annee':2026,'mois':null,'userId':'u'}"));
        golden("annee_texte_numerique_coercee_200", cloturer("{'annee':'2026','mois':3,'userId':'u'}"));
        golden("annee_texte_non_numerique", cloturer("{'annee':'deux mille','mois':3,'userId':'u'}"));
        golden("mois_decimal", cloturer("{'annee':2026,'mois':3.5,'userId':'u'}"));
        golden("mois_decimal_entier_3_0", cloturer("{'annee':2026,'mois':4.0,'userId':'u'}"));
        golden("proprietes_inconnues_ignorees_200", cloturer("{'annee':2026,'mois':5,'userId':'u','force':true,'rouvrir':true}"));
        golden("corps_json_illisible", cloturer("{'annee':2026,"));
        golden("corps_tableau", cloturer("[]"));
        golden("corps_null", cloturer("null"));
        golden("corps_absent", doPostSansCorps("/periodes/cloturer", adminA()));
        golden("corps_texte_plain", doPostTexte("/periodes/cloturer", "annee=2026&mois=3", adminA()));
        goldenPeriodes("validation_corps_etat", CENTRE_A);
    }

    // ------------------------------------------------------------------ rôles

    @Test
    void seul_l_administrateur_cloture() throws Exception {
        String corps = json("{'annee':2026,'mois':3,'userId':'u'}");
        golden("autorisation_secretaire_403", doPost("/periodes/cloturer", corps, secretaireA()));
        golden("autorisation_infirmier_403", doPost("/periodes/cloturer", corps, as(CENTRE_A, "INFIRMIER")));
        golden("autorisation_medecin_seul_role_scope_403", doPost("/periodes/cloturer", corps, as(CENTRE_A, "MEDECIN")));
        golden("autorisation_direction_role_scope_403", doPost("/periodes/cloturer", corps, as(CENTRE_A, "DIRECTION")));
        golden("autorisation_superadmin_role_scope_403", doPost("/periodes/cloturer", corps, as(CENTRE_A, "SUPERADMIN")));
        golden("autorisation_anonyme_403", send(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(java.net.URI.create(BASE + "/periodes/cloturer"))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(corps)));
        assertEquals(0, count("periodes_comptables", CENTRE_A));
        golden("autorisation_admin_200", doPost("/periodes/cloturer", corps, adminA()));
    }

    // ------------------------------------------------------------------ pas de réouverture

    @Test
    void aucune_route_ne_rouvre_ni_ne_liste_les_periodes() throws Exception {
        assertEquals(200, cloturer(2026, 3).status());
        golden("route_delete_periodes_cloturer_500", doRequest("DELETE", "/periodes/cloturer", adminA()));
        golden("route_put_periodes_cloturer_500", doRequest("PUT", "/periodes/cloturer", adminA()));
        golden("route_post_periodes_rouvrir_500", doRequest("POST", "/periodes/rouvrir", adminA()));
        golden("route_get_periodes_500", doRequest("GET", "/periodes", adminA()));
        goldenPeriodes("route_absentes_etat_inchange", CENTRE_A);
    }

    // ------------------------------------------------------------------ effet sur les écritures

    private ComptabiliteUseCase.GenererEcritureFacturationCommand facture(UUID id, String numero, String date) {
        return new ComptabiliteUseCase.GenererEcritureFacturationCommand(CENTRE_A, id, numero, UUID.randomUUID(), null,
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"), LocalDate.parse(date), "Séances");
    }

    private ComptabiliteUseCase.GenererEcritureReglementCommand reglement(UUID facture, UUID paiement, String date) {
        return new ComptabiliteUseCase.GenererEcritureReglementCommand(CENTRE_A, facture, paiement,
                new BigDecimal("50.00"), LocalDate.parse(date), "BANQUE", "Règlement", null);
    }

    private String codeRefus(Runnable action) {
        return assertThrows(BusinessException.class, action::run).getCode();
    }

    @Test
    void apres_la_cloture_d_un_mois_aucune_vente_ni_aucun_reglement_ne_s_y_comptabilise_mais_le_mois_suivant_reste_ouvert() throws Exception {
        assertEquals(200, cloturer(2026, 3).status());
        assertEquals("PERIODE_CLOTUREE", codeRefus(() -> comptabilite.genererEcritureFacturation(facture(UUID.randomUUID(), "F-31-MARS", "2026-03-31"))));
        assertEquals("PERIODE_CLOTUREE", codeRefus(() -> comptabilite.genererEcritureFacturation(facture(UUID.randomUUID(), "F-01-MARS", "2026-03-01"))));
        assertEquals("PERIODE_CLOTUREE", codeRefus(() -> comptabilite.genererEcritureReglement(reglement(UUID.randomUUID(), UUID.randomUUID(), "2026-03-15"))));
        assertEquals(0, count("ecritures_comptables", CENTRE_A));
        comptabilite.genererEcritureFacturation(facture(UUID.randomUUID(), "F-01-AVRIL", "2026-04-01"));
        comptabilite.genererEcritureFacturation(facture(UUID.randomUUID(), "F-28-FEV", "2026-02-28"));
        comptabilite.genererEcritureReglement(reglement(UUID.randomUUID(), UUID.randomUUID(), "2026-04-15"));
        goldenEcritures("cloture_mars_ecritures_avril_et_fevrier_creees_mars_refuse", CENTRE_A);
    }

    @Test
    void une_facture_ou_un_reglement_deja_comptabilises_dans_un_mois_clos_sont_rendus_tels_quels_sans_erreur() throws Exception {
        UUID facture = UUID.randomUUID();
        UUID paiement = UUID.randomUUID();
        comptabilite.genererEcritureFacturation(facture(facture, "F-MARS", "2026-03-10"));
        comptabilite.genererEcritureReglement(reglement(facture, paiement, "2026-03-20"));
        assertEquals(200, cloturer(2026, 3).status());
        comptabilite.genererEcritureFacturation(facture(facture, "F-MARS", "2026-03-10"));
        comptabilite.genererEcritureReglement(reglement(facture, paiement, "2026-03-20"));
        assertEquals(2, count("ecritures_comptables", CENTRE_A));
        goldenEcritures("idempotence_avant_controle_de_periode", CENTRE_A);
    }

    @Test
    void la_cloture_d_un_centre_n_empeche_pas_un_autre_centre_de_comptabiliser_le_meme_mois() throws Exception {
        assertEquals(200, cloturer(2026, 3).status());
        comptabilite.genererEcritureFacturation(new ComptabiliteUseCase.GenererEcritureFacturationCommand(CENTRE_B,
                UUID.randomUUID(), "F-B", UUID.randomUUID(), null, new BigDecimal("10.00"), BigDecimal.ZERO,
                new BigDecimal("10.00"), LocalDate.of(2026, 3, 10), "Séances"));
        assertEquals(1, count("ecritures_comptables", CENTRE_B));
    }

    @Test
    void la_validation_et_l_export_des_ecritures_d_un_mois_clos_restent_possibles() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "BROUILLON", "100.00");
        vente(2, "VE-2026-000002", "2026-03-11", "VALIDEE", "100.00");
        assertEquals(200, cloturer(2026, 3).status());
        golden("valider_dans_un_mois_clos_200", doPost("/ecritures/" + ecr(1) + "/valider", "", adminA()));
        goldenCsvTrie("exporter_un_mois_clos_200", doGet("/ecritures/export?from=2026-03-01&to=2026-03-31", adminA()));
        goldenEcritures("mois_clos_validee_et_exportee", CENTRE_A);
    }

    @Test
    void une_piece_saisie_dans_un_mois_clos_est_refusee_en_422_et_acceptee_le_mois_suivant() throws Exception {
        assertEquals(201, doPost("/modeles", json("{'code':'OD1','libelle':'Pièce','journal':'AC','actif':true,"
                + "'lignes':[{'sens':'DEBIT','compte':'706'},{'sens':'CREDIT','compte':'512'}]}"), adminA()).status());
        UUID modele = jdbc.queryForObject("SELECT id FROM modeles_piece WHERE center_id = ?", UUID.class, CENTRE_A);
        assertEquals(200, cloturer(2026, 3).status());
        golden("saisie_dans_un_mois_clos_422_periode_cloturee", doPost("/pieces", json("{'modeleId':'" + modele
                + "','date':'2026-03-31','montants':[10,10]}"), adminA()));
        assertEquals(0, count("ecritures_comptables", CENTRE_A));
        assertEquals(201, doPost("/pieces", json("{'modeleId':'" + modele + "','date':'2026-04-01','montants':[10,10]}"),
                adminA()).status());
        goldenEcritures("saisie_mois_suivant_creee", CENTRE_A);
    }

    @Test
    void la_comptabilisation_du_stock_ignore_une_reception_d_un_mois_clos() throws Exception {
        UUID article = UUID.fromString("99996300-0000-0000-0000-0000000000a1");
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, gere_par_lot, "
                + "active, pmp_courant, compte_stock, compte_charge) VALUES (?, ?, 'SER-10', 'SER-10', 'u', 0, 0, FALSE, TRUE, "
                + "10.00, NULL, NULL)", article, CENTRE_A);
        UUID bon = UUID.randomUUID();
        jdbc.update("INSERT INTO bons_reception (id, center_id, reference, date_reception, statut) VALUES (?, ?, ?, ?, ?)",
                bon, CENTRE_A, "BR-2026-0001", LocalDate.of(2026, 9, 3), "VALIDE");
        jdbc.update("INSERT INTO bons_reception_lignes (id, bon_reception_id, article_id, quantite, prix_unitaire) "
                + "VALUES (?, ?, ?, 100, 10.00)", UUID.randomUUID(), bon, article);
        assertEquals(200, cloturer(2026, 9).status());
        golden("stock_reception_en_mois_clos_ignoree_200",
                doPost("/stock/synchroniser", json("{'from':'2026-09-01','to':'2026-09-30'}"), adminA()));
        assertEquals(0, count("ecritures_comptables", CENTRE_A));
    }
}
