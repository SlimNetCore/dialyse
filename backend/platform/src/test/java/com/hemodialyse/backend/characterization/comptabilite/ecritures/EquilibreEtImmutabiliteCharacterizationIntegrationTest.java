package com.hemodialyse.backend.characterization.comptabilite.ecritures;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Filet de caractérisation C3 — équilibre débit/crédit au centime (RG-CPT-003, R2) et immutabilité des écritures
 * (RG-CPT-004, R4) : arrondi HALF_UP de chaque ligne avant la somme, refus d'une écriture déséquilibrée (rien n'est
 * créé), écriture exportée qui ne change plus jamais, écriture validée de sorties de stock réécrite en place tant
 * qu'elle n'est pas exportée (B9, propre à C6, constaté ici car il borne l'immutabilité d'une écriture validée).
 */
class EquilibreEtImmutabiliteCharacterizationIntegrationTest extends EcrituresCharacterizationSupport {

    @Override
    protected String theme() {
        return "equilibre-immutabilite";
    }

    // ------------------------------------------------------------------ équilibre

    private ObjectNode resultat(Cas cas, int numero) {
        ObjectNode o = mapper.createObjectNode();
        o.put("cas", cas.nom);
        o.put("ht", cas.ht);
        o.put("tva", cas.tva);
        o.put("ttc", cas.ttc);
        try {
            EcritureComptable e = genererVente(numero, cas.ht, cas.tva, cas.ttc, "2026-03-05");
            o.put("resultat", "ECRITURE_CREEE");
            o.put("totalDebit", e.totalDebit().toPlainString());
            ArrayNode lignes = o.putArray("lignes");
            e.getLignes().stream()
                    .sorted(Comparator.comparing(LigneEcriture::getCompteSCF)
                            .thenComparing(LigneEcriture::getMontantDebit).thenComparing(LigneEcriture::getMontantCredit))
                    .forEach(l -> lignes.add(l.getCompteSCF() + " D=" + l.getMontantDebit().toPlainString()
                            + " C=" + l.getMontantCredit().toPlainString()));
        } catch (com.hemodialyse.backend.domain.shared.exception.BusinessException b) {
            o.put("resultat", "REFUS_METIER");
            o.put("code", b.getCode());
            o.put("message", b.getMessage());
        } catch (RuntimeException r) {
            o.put("resultat", "ERREUR_TECHNIQUE");
            o.put("type", r.getClass().getSimpleName());
            o.put("message", r.getMessage());
        }
        o.put("ecrituresEnBase", count("ecritures_comptables", CENTRE_A));
        return o;
    }

    @Test
    void chaque_ligne_est_arrondie_au_centime_demi_haut_avant_la_somme_et_une_ecriture_deseguilibree_n_est_pas_creee() throws Exception {
        List<Cas> cas = List.of(
                new Cas("nominal_19_pour_cent", "100.00", "19.00", "119.00"),
                new Cas("arrondi_par_ligne_avant_somme_995", "99.995", "19.00", "118.995"),
                new Cas("demi_centime_seul", "0.005", "0", "0.005"),
                new Cas("ecart_d_un_centime_en_moins", "100.00", "19.00", "118.99"),
                new Cas("ecart_d_un_centime_en_plus", "100.00", "19.00", "119.01"),
                new Cas("tva_zero_et_ttc_superieur_tva_derivee", "100.00", "0", "119.00"),
                new Cas("tva_negative_et_ttc_superieur_tva_derivee", "100.00", "-5.00", "119.00"),
                new Cas("tva_negative_et_ttc_egal_sans_ligne_de_tva", "100.00", "-5.00", "100.00"),
                new Cas("trois_tiers_somme_des_arrondis_differente", "33.333", "6.333", "39.666"),
                new Cas("demi_haut_sur_chaque_ligne_equilibre", "10.005", "1.90", "11.905"),
                new Cas("montants_nuls", "0", "0", "0"),
                new Cas("avoir_montants_negatifs", "-100.00", "0", "-100.00"),
                new Cas("ttc_inferieur_au_ht", "100.00", "0", "90.00"),
                new Cas("grands_montants", "123456789.01", "23456790.11", "146913579.12"),
                new Cas("tva_a_trois_decimales_arrondie_a_19_00", "100.00", "19.004", "119.004"),
                new Cas("tva_19_005_arrondie_a_19_01", "100.00", "19.005", "119.005"));
        ArrayNode tout = mapper.createArrayNode();
        for (int i = 0; i < cas.size(); i++) {
            tout.add(resultat(cas.get(i), 100 + i));
            jdbc.update("DELETE FROM lignes_ecriture WHERE ecriture_id IN (SELECT id FROM ecritures_comptables WHERE center_id = ?)", CENTRE_A);
            jdbc.update("DELETE FROM ecritures_comptables WHERE center_id = ?", CENTRE_A);
        }
        goldenNode("equilibre_ventes_cas_d_arrondi", tout);
    }

    @Test
    void un_reglement_est_toujours_equilibre_sa_valeur_absolue_est_prise_et_les_demi_centimes_arrondis() throws Exception {
        ArrayNode tout = mapper.createArrayNode();
        int i = 0;
        for (String montant : new String[]{"100.00", "-100.00", "0", "0.005", "-0.005", "1234567.895"}) {
            ObjectNode o = mapper.createObjectNode();
            o.put("montant", montant);
            EcritureComptable e = genererReglement(CENTRE_A, fid(1), rid(++i), montant, "2026-03-06", "BANQUE", null);
            ArrayNode lignes = o.putArray("lignes");
            e.getLignes().stream().sorted(Comparator.comparing(LigneEcriture::getCompteSCF)).forEach(l -> lignes.add(
                    l.getCompteSCF() + " D=" + l.getMontantDebit().toPlainString() + " C=" + l.getMontantCredit().toPlainString()));
            tout.add(o);
        }
        goldenNode("equilibre_reglements_valeur_absolue_et_arrondis", tout);
    }

    private List<Map<String, Object>> etat() {
        return jdbc.queryForList("SELECT e.id, e.journal_code, e.numero_piece, e.date_ecriture, e.date_piece, e.libelle, "
                + "e.statut, e.source_id, e.created_at, e.updated_at, l.id AS ligne, l.compte_scf, l.libelle_ligne, "
                + "l.montant_debit, l.montant_credit, l.tiers_id FROM ecritures_comptables e "
                + "JOIN lignes_ecriture l ON l.ecriture_id = e.id WHERE e.id = ? ORDER BY l.id", ecr(1));
    }

    // ------------------------------------------------------------------ immutabilité d'une écriture exportée

    @Test
    void une_ecriture_exportee_ne_change_plus_ni_par_validation_ni_par_reexport_ni_par_cloture_ni_par_regeneration() throws Exception {
        ecriture(1, CENTRE_A, "VE", "VE-2026-000001", "2026-03-10", "2026-03-10", "EXPORTEE", "Facture exportée", fid(1), null,
                L.d("411100", "119.00"), L.c("706", "100.00"), L.c("44571", "19.00"));
        List<Map<String, Object>> avant = etat();
        assertEquals(422, doPost("/ecritures/" + ecr(1) + "/valider", "", adminA()).status());
        assertEquals(200, doGet("/ecritures/export?from=2026-03-01&to=2026-03-31", adminA()).status());
        assertEquals(200, doPost("/periodes/cloturer", json("{'annee':2026,'mois':3,'userId':'u'}"), adminA()).status());
        assertEquals(200, doGet("/ecritures/export?from=2026-03-01&to=2026-03-31", adminA()).status());
        genererVente(CENTRE_A, fid(1), "FACT-1", "999.00", "0.00", "999.00", "2026-03-10", null);
        assertEquals(avant, etat());
        assertEquals("EXPORTEE", statutEnBase(1));
    }

    @Test
    void aucune_route_ne_modifie_ni_ne_supprime_une_ecriture() throws Exception {
        vente(1, "VE-2026-000001", "2026-03-10", "EXPORTEE", "10.00");
        vente(2, "VE-2026-000002", "2026-03-10", "BROUILLON", "10.00");
        List<Map<String, Object>> avant = etat();
        for (String methode : new String[]{"PUT", "PATCH", "DELETE"}) {
            for (int n : new int[]{1, 2}) {
                assertEquals(500, doRequest(methode, "/ecritures/" + ecr(n), adminA()).status());
            }
        }
        assertEquals(2, count("ecritures_comptables", CENTRE_A));
        assertEquals(avant, etat());
    }

    private void article() {
        jdbc.update("INSERT INTO articles (id, center_id, code, libelle, unite, stock_quantity, seuil_alerte, gere_par_lot, "
                + "active, pmp_courant, compte_stock, compte_charge) VALUES (?, ?, 'SER-10', 'SER-10', 'u', 0, 0, FALSE, TRUE, "
                + "10.00, NULL, NULL)", UUID.fromString("99996300-0000-0000-0000-0000000000a1"), CENTRE_A);
    }

    // ------------------------------------------------------------------ B9 : sorties de stock validées réécrites en place

    private void sortie(String quantite, String jour, int heure) {
        jdbc.update("INSERT INTO stock_movements (id, center_id, article_id, mouvement_type, quantite, prix_unitaire, "
                        + "pmp_apres, created_at) VALUES (?, ?, ?, 'SORTIE', ?, 10.00, NULL, ?)", UUID.randomUUID(), CENTRE_A,
                UUID.fromString("99996300-0000-0000-0000-0000000000a1"), new BigDecimal(quantite),
                Timestamp.from(LocalDate.parse(jour).atTime(heure, 0).toInstant(ZoneOffset.UTC)));
    }

    private Resp synchroniser() throws Exception {
        return doPost("/stock/synchroniser", json("{'from':'2026-09-01','to':'2026-09-30'}"), adminA());
    }

    @Test
    void les_sorties_de_stock_d_un_jour_validees_sont_reecrites_en_place_tant_qu_elles_ne_sont_pas_exportees_puis_completees() throws Exception {
        article();
        sortie("20", "2026-09-05", 9);
        golden("stock_1_premiere_synchronisation_200", synchroniser());
        goldenEcritures("stock_1_ecritures", CENTRE_A);
        goldenLignes("stock_1_lignes", CENTRE_A);
        sortie("5", "2026-09-05", 10);
        golden("stock_2_sortie_supplementaire_synchronisation_200", synchroniser());
        goldenEcritures("stock_2_ecriture_validee_reecrite_en_place", CENTRE_A);
        goldenLignes("stock_2_lignes", CENTRE_A);
        goldenCsvTrie("stock_3_export_du_journal_st_200", doGet("/ecritures/export?from=2026-09-01&to=2026-09-30&journalCode=ST", adminA()));
        goldenEcritures("stock_3_apres_export", CENTRE_A);
        sortie("3", "2026-09-05", 11);
        golden("stock_4_sortie_apres_export_synchronisation_200", synchroniser());
        goldenEcritures("stock_4_ecriture_de_complement", CENTRE_A);
        goldenLignes("stock_4_lignes", CENTRE_A);
    }

    private record Cas(String nom, String ht, String tva, String ttc) {
    }
}
