package com.hemodialyse.backend.characterization.comptabilite.ecritures;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet C4 : contrats HTTP et effets SQL des modèles de pièces, de la saisie et de l'extourne.
 */
class ModelesEtPiecesCharacterizationIntegrationTest extends EcrituresCharacterizationSupport {

    private static final String MODELES = "/modeles";
    private static final String SAISIR = "/pieces";

    @Override
    protected String theme() {
        return "pieces";
    }

    private String modele(String code, String journal, boolean actif, String compteDebit, String compteCredit) {
        return json("{'code':'" + code + "','libelle':'Loyer du centre','journal':'" + journal + "','actif':"
                + actif + ",'lignes':[{'sens':'DEBIT','compte':'" + compteDebit
                + "','libelle':'Loyer'},{'sens':'CREDIT','compte':'" + compteCredit + "'}]}");
    }

    private UUID creerModele(int n) throws Exception {
        Resp r = doPost(MODELES, modele("loyer" + n, "AC", true, "613", "401"), adminA());
        return UUID.fromString(json(r).path("id").asString());
    }

    private UUID creerModeleAvecLigneIntermediaire(int n) throws Exception {
        String request = json("{'code':'loyer" + n + "','libelle':'Loyer du centre','journal':'AC','actif':true,"
                + "'lignes':[{'sens':'DEBIT','compte':'613','libelle':'Loyer'},"
                + "{'sens':'DEBIT','compte':'4456'},{'sens':'CREDIT','compte':'401'}]}");
        Resp r = doPost(MODELES, request, adminA());
        return UUID.fromString(json(r).path("id").asString());
    }

    private Resp saisir(UUID modele, String date, String libelle, String montants, RequestPostProcessor who)
            throws Exception {
        String corps = json("{'modeleId':'" + modele + "','date':'" + date + "','libelle':" + libelle
                + ",'montants':" + montants + "}");
        return doPost(SAISIR, corps, who);
    }

    @Test
    void lister_modeles_est_pagine_et_filtre_les_inactifs() throws Exception {
        doPost(MODELES, modele("LOYER", "AC", true, "613", "401"), adminA());
        doPost(MODELES, modele("PAIE", "AC", false, "641", "421"), adminA());
        golden("modeles_page_par_defaut_200", doGet(MODELES, secretaireA()));
        golden("modeles_actifs_seulement_200", doGet(MODELES + "?actifs=true&page=0&size=1", adminA()));
        golden("modeles_autre_centre_200", doGet(MODELES, adminB()));
    }

    @Test
    void creer_modifier_et_supprimer_un_modele_garde_le_code_unique_par_centre() throws Exception {
        Resp cree = doPost(MODELES, modele("loyer", "AC", true, "613", "401"), adminA());
        golden("modele_creation_201", cree);
        UUID id = UUID.fromString(json(cree).path("id").asString());
        goldenSql("modele_creation_etat", "SELECT code, libelle, journal_code, actif FROM modeles_piece "
                + "WHERE center_id = ? ORDER BY code", CENTRE_A);
        goldenSql("modele_creation_lignes", "SELECT position_ligne, sens, compte, libelle FROM modeles_piece_lignes "
                + "WHERE center_id = ? ORDER BY modele_id, position_ligne", CENTRE_A);

        golden("modele_code_duplique_422", doPost(MODELES, modele("LOYER", "AC", true, "613", "401"), adminA()));
        golden("modele_modification_200", doPut(MODELES + "/" + id,
                modele("loyer", "AC", false, "613", "401"), adminA()));
        golden("modele_liste_inactif_200", doGet(MODELES + "?actifs=true", adminA()));
        Resp supprime = doDelete(MODELES + "/" + id, adminA());
        golden("modele_suppression_204", supprime);
        goldenSql("modele_apres_suppression", "SELECT code FROM modeles_piece WHERE center_id = ? ORDER BY code",
                CENTRE_A);
    }

    @Test
    void les_modeles_refusent_journal_compte_et_lignes_invalides() throws Exception {
        golden("modele_journal_inconnu_422", doPost(MODELES,
                modele("PAIE", "ZZ", true, "613", "401"), adminA()));
        golden("modele_compte_inconnu_422", doPost(MODELES,
                modele("PAIE", "AC", true, "999999", "401"), adminA()));
        golden("modele_sens_invalide_400", doPost(MODELES,
                json("{'code':'PAIE','libelle':'Paie','journal':'AC','actif':true,'lignes':["
                        + "{'sens':'AUTRE','compte':'613'},{'sens':'CREDIT','compte':'401'}]}"), adminA()));
        golden("modele_sans_cote_credit_422", doPost(MODELES,
                json("{'code':'PAIE','libelle':'Paie','journal':'AC','actif':true,'lignes':["
                        + "{'sens':'DEBIT','compte':'613'},{'sens':'DEBIT','compte':'401'}]}"), adminA()));
        goldenSql("modele_invalide_aucune_ecriture", "SELECT COUNT(*) AS total FROM modeles_piece WHERE center_id = ?",
                CENTRE_A);
    }

    @Test
    void les_mutations_de_modele_sont_isolees_et_reservees_a_admin() throws Exception {
        UUID id = creerModele(1);
        golden("modele_autre_centre_403", doGet(MODELES + "?centerId=" + CENTRE_B, adminA()));
        golden("modele_modification_autre_centre_500", doPut(MODELES + "/" + id,
                modele("loyer1", "AC", true, "613", "401"), adminB()));
        golden("modele_suppression_autre_centre_422", doDelete(MODELES + "/" + id, adminB()));
        golden("modele_creation_secretaire_403", doPost(MODELES,
                modele("PAIE", "AC", true, "641", "421"), secretaireA()));
        golden("modele_suppression_secretaire_403", doDelete(MODELES + "/" + id, secretaireA()));
    }

    @Test
    void saisir_une_piece_cree_une_ecriture_validee_et_equilibree() throws Exception {
        UUID modele = creerModele(2);
        Resp piece = saisir(modele, "2026-03-10", "'Loyer mars'", "[100000,100000]", secretaireA());
        golden("piece_saisie_201", piece);
        goldenEcritures("piece_saisie_ecriture", CENTRE_A);
        goldenLignes("piece_saisie_lignes", CENTRE_A);
        goldenSql("piece_saisie_lien_modele", "SELECT modele_id, source_id, statut FROM ecritures_comptables "
                + "WHERE center_id = ? ORDER BY numero_piece", CENTRE_A);
    }

    @Test
    void saisir_une_piece_omet_les_montants_nuls_et_prend_le_libelle_du_modele_par_defaut() throws Exception {
        UUID modele = creerModeleAvecLigneIntermediaire(3);
        Resp piece = saisir(modele, "2026-03-10", "null", "[500,0,500]", adminA());
        golden("piece_sans_libelle_et_ligne_nulle_201", piece);
        goldenLignes("piece_lignes_nulles_omises", CENTRE_A);
    }

    @Test
    void saisir_refuse_modele_inactif_montants_invalides_desequilibre_et_periode_cloturee() throws Exception {
        Resp inactif = doPost(MODELES, modele("inactif", "AC", false, "613", "401"), adminA());
        UUID idInactif = UUID.fromString(json(inactif).path("id").asString());
        golden("piece_modele_inactif_422", saisir(idInactif, "2026-03-10", "null", "[10,10]", adminA()));

        UUID modele = creerModele(4);
        golden("piece_nombre_montants_invalide_422", saisir(modele, "2026-03-10", "null", "[10]", adminA()));
        golden("piece_montant_negatif_422", saisir(modele, "2026-03-10", "null", "[-1,1]", adminA()));
        golden("piece_montants_nuls_422", saisir(modele, "2026-03-10", "null", "[0,0]", adminA()));
        golden("piece_desequilibree_422", saisir(modele, "2026-03-10", "null", "[10,9]", adminA()));
        periode(CENTRE_A, 2026, 3, true, "comptable");
        golden("piece_periode_cloturee_422", saisir(modele, "2026-03-10", "null", "[10,10]", adminA()));
        goldenSql("piece_refusee_aucune_ecriture", "SELECT COUNT(*) AS total FROM ecritures_comptables "
                + "WHERE center_id = ?", CENTRE_A);
    }

    @Test
    void extourner_une_piece_cree_une_ecriture_inverse_datee_du_jour() throws Exception {
        UUID modele = creerModele(5);
        Resp saisie = saisir(modele, "2026-03-10", "'Loyer mars'", "[100,100]", adminA());
        UUID id = UUID.fromString(json(saisie).path("id").asString());
        goldenAujourdhui("piece_extourne_201", doPostSansCorps("/pieces/" + id + "/extourne", adminA()));
        goldenEcrituresAvecAujourdhui("piece_et_extourne_ecritures", CENTRE_A);
        goldenLignes("piece_et_extourne_lignes", CENTRE_A);
        assertEquals(new BigDecimal("0.00"),
                jdbc.queryForObject("SELECT SUM(l.montant_debit - l.montant_credit) FROM lignes_ecriture l "
                        + "JOIN ecritures_comptables e ON e.id = l.ecriture_id WHERE e.center_id = ? "
                        + "AND l.compte_scf = '613'", BigDecimal.class, CENTRE_A));
    }

    @Test
    void extourne_est_refusee_pour_une_ecriture_systeme_une_seconde_fois_ou_un_autre_centre() throws Exception {
        UUID modele = creerModele(6);
        Resp saisie = saisir(modele, "2026-03-10", "'Loyer mars'", "[100,100]", adminA());
        UUID id = UUID.fromString(json(saisie).path("id").asString());
        golden("extourne_autre_centre_422", doPostSansCorps("/pieces/" + id + "/extourne", adminB()));
        goldenAujourdhui("extourne_nominale_201", doPostSansCorps("/pieces/" + id + "/extourne", adminA()));
        golden("extourne_piece_deja_extournee_422", doPostSansCorps("/pieces/" + id + "/extourne", adminA()));
        String extourne = jdbc.queryForObject("SELECT CAST(id AS VARCHAR(36)) FROM ecritures_comptables "
                + "WHERE center_id = ? AND source_id <> id AND source_id IS NOT NULL", String.class, CENTRE_A);
        golden("extourne_extourne_422", doPostSansCorps("/pieces/" + extourne + "/extourne", adminA()));
        vente(20, "VE-2026-000020", "2026-03-10", "VALIDEE", "10.00");
        golden("extourne_ecriture_systeme_422", doPostSansCorps("/pieces/" + ecr(20) + "/extourne", adminA()));
    }

    @Test
    void supprimer_un_modele_ne_supprime_pas_les_pieces_existantes() throws Exception {
        Resp cree = doPost(MODELES, modele("LOYER", "AC", true, "613", "401"), adminA());
        UUID id = UUID.fromString(json(cree).path("id").asString());
        saisir(id, "2026-03-10", "'Loyer mars'", "[100,100]", adminA());
        golden("modele_suppression_apres_saisie_204", doDelete(MODELES + "/" + id, adminA()));
        goldenEcritures("modele_supprime_piece_conservee", CENTRE_A);
        goldenLignes("modele_supprime_lignes_conservees", CENTRE_A);
    }
}


