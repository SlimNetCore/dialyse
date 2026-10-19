package com.hemodialyse.backend.characterization.comptabilite.ecritures;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Filet C6 : comptabilisation du stock ({@code POST /stock/synchroniser}, même cas d'usage que la tâche de nuit) sur
 * une vraie base H2. Observation : réponse (compteurs), écritures et lignes produites. Les bons de réception et
 * inventaires ont des identifiants aléatoires (normalisés par ordre d'apparition) ; les sources des journées de sorties
 * sont déterministes. Données de septembre 2026 ; « aujourd'hui » (UTC) n'intervient que pour les compléments d'un mois
 * clos, normalisé {@code <AUJOURDHUI_UTC>}. Les goldens {@code *_nouveau_code} portent le champ {@code echecs} décidé le
 * 2026-10-19 (KB-CPT-42 et 44) ; écritures et lignes sont inchangées.
 */
class ComptabilisationDuStockCharacterizationIntegrationTest extends EcrituresCharacterizationSupport {

    private static final String SYNCHRONISER = "/stock/synchroniser";
    private static final String SEPTEMBRE = "{'from':'2026-09-01','to':'2026-09-30'}";
    private static final UUID SERINGUE = UUID.fromString("99996300-0000-0000-0006-000000000001");
    private static final UUID EPO = UUID.fromString("99996300-0000-0000-0006-000000000002");
    private static final UUID GANTS = UUID.fromString("99996300-0000-0000-0006-000000000003");
    private static final UUID ARTICLE_B = UUID.fromString("99996300-0000-0000-0006-000000000004");

    private static Timestamp instant(String iso) {
        return Timestamp.from(java.time.OffsetDateTime.parse(iso).toInstant());
    }

    // ------------------------------------------------------------------ fixtures

    @Override
    protected String theme() {
        return "stock";
    }

    private void article(UUID id, UUID centre, String code, String compteStock, String compteCharge, String pmp) {
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, "
                        + "gere_par_lot, active, pmp_courant, compte_stock, compte_charge) "
                        + "VALUES (?, ?, ?, ?, 'u', 0, 0, FALSE, TRUE, ?, ?, ?)",
                id, centre, code, code, pmp == null ? null : new BigDecimal(pmp), compteStock, compteCharge);
    }

    private UUID reception(UUID centre, String reference, String date, String statut) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO bons_reception (id, center_id, reference, date_reception, statut) VALUES (?, ?, ?, ?, ?)",
                id, centre, reference, date == null ? null : LocalDate.parse(date), statut);
        return id;
    }

    private void ligneReception(UUID bon, UUID article, String quantite, String prix) {
        jdbc.update("INSERT INTO bons_reception_lignes (id, bon_reception_id, article_id, quantite, prix_unitaire) "
                        + "VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), bon, article, new BigDecimal(quantite),
                prix == null ? null : new BigDecimal(prix));
    }

    private UUID mouvement(UUID centre, UUID article, String type, String quantite, String prix, String pmpApres,
                           String quand) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, mouvement_type, quantite, prix_unitaire, "
                        + "pmp_apres, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, centre, article, type, new BigDecimal(quantite), prix == null ? null : new BigDecimal(prix),
                pmpApres == null ? null : new BigDecimal(pmpApres), instant(quand));
        return id;
    }

    private UUID inventaire(UUID centre, String reference, String date, String statut) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO inventaires (id, center_id, reference, date_inventaire, statut, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", id, centre, reference, LocalDate.parse(date), statut,
                instant(date + "T08:00:00Z"));
        return id;
    }

    private void ligneInventaire(UUID inventaire, UUID centre, int position, UUID article, String theorique,
                                 String comptee, String pmp) {
        jdbc.update("INSERT INTO inventaire_lignes (id, inventaire_id, center_id, position_ligne, article_id, "
                        + "quantite_theorique, quantite_comptee, pmp) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), inventaire, centre, position, article, new BigDecimal(theorique),
                comptee == null ? null : new BigDecimal(comptee), pmp == null ? null : new BigDecimal(pmp));
    }

    private Resp synchroniser(String corps) throws Exception {
        return doPost(SYNCHRONISER, json(corps), adminA());
    }

    /**
     * Un mois de stock « complet » : réceptions, sorties sur trois jours, inventaires, données parasites ignorées.
     */
    private void moisDeSeptembre() {
        article(SERINGUE, CENTRE_A, "SER-10", null, null, "12.00");
        article(EPO, CENTRE_A, "EPO-4000", "321", "6021", "2500.00");
        article(GANTS, CENTRE_A, "GANTS", null, null, null);
        article(ARTICLE_B, CENTRE_B, "SER-10", null, null, "9.00");

        UUID bon = reception(CENTRE_A, "BR-2026-0001", "2026-09-03", "VALIDE");
        ligneReception(bon, SERINGUE, "100", "10.00");
        ligneReception(bon, EPO, "4", "2500.00");
        ligneReception(bon, GANTS, "3", "3.335");
        ligneReception(reception(CENTRE_A, "BR-2026-0002", "2026-09-04", "BROUILLON"), SERINGUE, "5", "10.00");
        ligneReception(reception(CENTRE_A, "BR-2026-0003", "2026-09-10", "VALIDE"), GANTS, "10", null);
        ligneReception(reception(CENTRE_B, "BR-2026-0001", "2026-09-03", "VALIDE"), ARTICLE_B, "7", "9.00");

        mouvement(CENTRE_A, SERINGUE, "SORTIE", "20", "10.00", null, "2026-09-05T09:00:00Z");
        mouvement(CENTRE_A, EPO, "SORTIE", "1", null, "2500.00", "2026-09-05T15:00:00Z");
        mouvement(CENTRE_A, SERINGUE, "SORTIE", "-3", null, null, "2026-09-06T23:30:00Z");
        mouvement(CENTRE_A, SERINGUE, "SORTIE", "2", "10.00", null, "2026-09-07T00:30:00+01:00");
        mouvement(CENTRE_A, GANTS, "SORTIE", "1", null, null, "2026-09-08T10:00:00Z");
        mouvement(CENTRE_A, SERINGUE, "ENTREE", "100", "10.00", null, "2026-09-03T09:00:00Z");
        mouvement(CENTRE_B, ARTICLE_B, "SORTIE", "2", "9.00", null, "2026-09-05T09:00:00Z");

        UUID inv = inventaire(CENTRE_A, "INV-2026-0001", "2026-09-20", "CLOTURE");
        ligneInventaire(inv, CENTRE_A, 1, SERINGUE, "77", "79", "10.00");
        ligneInventaire(inv, CENTRE_A, 2, EPO, "3", "2", "2500.00");
        ligneInventaire(inv, CENTRE_A, 3, GANTS, "5", null, "1.00");
        ligneInventaire(inventaire(CENTRE_A, "INV-2026-0002", "2026-09-25", "EN_COURS"), CENTRE_A, 1, SERINGUE,
                "79", "70", "10.00");
    }

    // ------------------------------------------------------------------ scénarios

    @Test
    void un_mois_de_stock_donne_une_ecriture_par_bon_valide_par_jour_de_sorties_et_par_inventaire_cloture()
            throws Exception {
        moisDeSeptembre();

        golden("septembre_premier_passage_200_nouveau_code", synchroniser(SEPTEMBRE));
        goldenEcritures("septembre_ecritures", CENTRE_A);
        goldenLignes("septembre_lignes", CENTRE_A);
        assertEquals(0, count("ecritures_comptables", CENTRE_B));
    }

    @Test
    void un_rejeu_ne_cree_rien_et_un_mouvement_tardif_met_a_jour_l_ecriture_du_jour_en_place() throws Exception {
        moisDeSeptembre();
        synchroniser(SEPTEMBRE);

        golden("rejeu_sans_changement_200_nouveau_code", synchroniser(SEPTEMBRE));
        mouvement(CENTRE_A, SERINGUE, "SORTIE", "5", "10.00", null, "2026-09-05T18:00:00Z");
        golden("mouvement_tardif_200", synchroniser(SEPTEMBRE));
        goldenEcritures("mouvement_tardif_ecritures", CENTRE_A);
        goldenLignes("mouvement_tardif_lignes", CENTRE_A);
    }

    @Test
    void des_sorties_qui_diminuent_ou_disparaissent_sont_corrigees_par_la_difference() throws Exception {
        article(SERINGUE, CENTRE_A, "SER-10", null, null, "12.00");
        UUID matin = mouvement(CENTRE_A, SERINGUE, "SORTIE", "20", "10.00", null, "2026-09-05T09:00:00Z");
        mouvement(CENTRE_A, SERINGUE, "SORTIE", "5", "10.00", null, "2026-09-05T10:00:00Z");
        UUID seul = mouvement(CENTRE_A, SERINGUE, "SORTIE", "3", "10.00", null, "2026-09-06T09:00:00Z");
        synchroniser(SEPTEMBRE);

        jdbc.update("DELETE FROM stock_movements WHERE id IN (?, ?)", matin, seul);
        golden("sorties_diminuees_et_disparues_200", synchroniser(SEPTEMBRE));
        goldenEcritures("sorties_diminuees_et_disparues_ecritures", CENTRE_A);
        goldenLignes("sorties_diminuees_et_disparues_lignes", CENTRE_A);
    }

    @Test
    void une_journee_deja_exportee_recoit_un_complement_date_du_jour_d_origine() throws Exception {
        article(SERINGUE, CENTRE_A, "SER-10", null, null, "12.00");
        mouvement(CENTRE_A, SERINGUE, "SORTIE", "3", "10.00", null, "2026-09-06T09:00:00Z");
        synchroniser(SEPTEMBRE);
        jdbc.update("UPDATE ecritures_comptables SET statut = 'EXPORTEE' WHERE center_id = ?", CENTRE_A);

        mouvement(CENTRE_A, SERINGUE, "SORTIE", "5", "10.00", null, "2026-09-06T18:00:00Z");
        golden("journee_exportee_complement_200", synchroniser(SEPTEMBRE));
        goldenEcritures("journee_exportee_complement_ecritures", CENTRE_A);
        goldenLignes("journee_exportee_complement_lignes", CENTRE_A);
    }

    @Test
    void dans_un_mois_clos_les_pieces_sont_ignorees_et_un_complement_de_sorties_est_date_d_aujourd_hui()
            throws Exception {
        moisDeSeptembre();
        synchroniser(SEPTEMBRE);
        periode(CENTRE_A, 2026, 9, true, "compta");

        mouvement(CENTRE_A, SERINGUE, "SORTIE", "5", "10.00", null, "2026-09-06T18:00:00Z");
        ligneReception(reception(CENTRE_A, "BR-2026-0004", "2026-09-12", "VALIDE"), SERINGUE, "1", "10.00");
        UUID inv = inventaire(CENTRE_A, "INV-2026-0003", "2026-09-28", "CLOTURE");
        ligneInventaire(inv, CENTRE_A, 1, SERINGUE, "10", "9", "10.00");

        golden("mois_clos_200_nouveau_code", synchroniser(SEPTEMBRE));
        goldenEcrituresAvecAujourdhui("mois_clos_ecritures", CENTRE_A);
    }

    @Test
    void si_le_mois_d_aujourd_hui_est_aussi_clos_le_complement_est_ignore() throws Exception {
        article(SERINGUE, CENTRE_A, "SER-10", null, null, "12.00");
        mouvement(CENTRE_A, SERINGUE, "SORTIE", "3", "10.00", null, "2026-09-06T09:00:00Z");
        synchroniser(SEPTEMBRE);
        LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);
        periode(CENTRE_A, 2026, 9, true, "compta");
        if (!(aujourdhui.getYear() == 2026 && aujourdhui.getMonthValue() == 9)) {
            periode(CENTRE_A, aujourdhui.getYear(), aujourdhui.getMonthValue(), true, "compta");
        }
        mouvement(CENTRE_A, SERINGUE, "SORTIE", "5", "10.00", null, "2026-09-06T18:00:00Z");

        Resp r = synchroniser(SEPTEMBRE);
        // le refus cite le mois d'aujourd'hui (UTC), normalisé pour que le golden ne dépende pas de la date
        golden("mois_d_aujourd_hui_clos_200_nouveau_code", new Resp(r.status(), r.contentType(), r.disposition(),
                r.raw().replace(java.time.YearMonth.from(aujourdhui).toString(), "<MOIS_COURANT_UTC>")));
        assertEquals(1, count("ecritures_comptables", CENTRE_A));
    }

    @Test
    void les_journaux_du_stock_suivent_le_mapping_du_centre() throws Exception {
        assertEquals(200, doPut("/journaux/OD", json("{'libelle':'Opérations diverses','actif':true}"), adminA()).status());
        assertEquals(200, doPut("/mapping", json("{'compteVentes':'706','compteClientPatient':'411100',"
                + "'compteClientDefaut':'411500','compteBanque':'512','compteCaisse':'530',"
                + "'journaux':{'STOCK_RECEPTION':'OD','STOCK_SORTIE':'OD','STOCK_INVENTAIRE':'OD'}}"), adminA()).status());
        moisDeSeptembre();

        golden("mapping_journaux_od_200_nouveau_code", synchroniser(SEPTEMBRE));
        goldenEcritures("mapping_journaux_od_ecritures", CENTRE_A);
    }

    @Test
    void la_route_controle_roles_centre_et_periode() throws Exception {
        golden("periode_inversee_400", synchroniser("{'from':'2026-09-30','to':'2026-09-01'}"));
        golden("periode_de_367_jours_400", synchroniser("{'from':'2025-09-01','to':'2026-09-03'}"));
        golden("periode_de_366_jours_200", synchroniser("{'from':'2025-09-01','to':'2026-09-02'}"));
        golden("sans_from_400", synchroniser("{'to':'2026-09-30'}"));
        golden("secretaire_403", doPost(SYNCHRONISER, json(SEPTEMBRE), secretaireA()));
        golden("autre_centre_403", synchroniser("{'centerId':'" + CENTRE_B + "','from':'2026-09-01','to':'2026-09-30'}"));
        golden("corps_absent_500", send(post(java.net.URI.create(BASE + SYNCHRONISER)).with(adminA())));
        Resp anonyme = send(post(java.net.URI.create(BASE + SYNCHRONISER)).contentType(MediaType.APPLICATION_JSON)
                .content(json(SEPTEMBRE)));
        goldenNode("anonyme", mapper.createObjectNode().put("status", anonyme.status()));
    }
}

