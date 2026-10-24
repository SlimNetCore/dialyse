package com.hemodialyse.backend.contexts.stock.domain.inventaire;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Règles de l'inventaire (RG-STK-050 à 057, S7) : théorique figé, comptage, lignes ajoutées, report, clôture, annulation, et
 * contrôles d'ouverture de la période de stock. Les codes sont ceux du legacy (filet de caractérisation).
 */
class InventaireTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final OffsetDateTime MAINTENANT = OffsetDateTime.parse("2026-10-24T10:00:00Z");
    private static final LocalDate HIER = LocalDate.of(2026, 10, 23);

    private final UUID article = UUID.randomUUID();
    private final UUID autreArticle = UUID.randomUUID();

    private LigneDInventaire ligne(UUID art, String numeroLot, String theorique) {
        return LigneDInventaire.theorique(UUID.randomUUID(), art, "A", "Article", "u", UUID.randomUUID(), numeroLot,
                LocalDate.of(2027, 1, 1), new BigDecimal(theorique), new BigDecimal("5"));
    }

    private Inventaire ouvert(LigneDInventaire... lignes) {
        return Inventaire.ouvrir(UUID.randomUUID(), CENTRE, "INV-00001", HIER, "  annuel  ", "pharmacien", MAINTENANT, List.of(lignes));
    }

    private String code(Runnable action) {
        return assertThrows(RefusDuReferentiel.class, action::run).code();
    }

    @Test
    void l_ouverture_exige_une_date_non_future_et_fige_le_theorique() {
        LigneDInventaire l = ligne(article, "L1", "3");
        Inventaire inv = ouvert(l);
        assertEquals(StatutDInventaire.EN_COURS, inv.statut());
        assertEquals("annuel", inv.commentaire());
        assertEquals(1, inv.lignes().size());
        assertEquals("INVENTORY_DATE_REQUIRED", code(() -> Inventaire.ouvrir(UUID.randomUUID(), CENTRE, "R", null, null, "p", MAINTENANT, List.of())));
        assertEquals("INVENTORY_DATE_IN_FUTURE", code(() -> Inventaire.ouvrir(UUID.randomUUID(), CENTRE, "R", LocalDate.of(2026, 10, 25), null,
                "p", MAINTENANT, List.of())));
    }

    @Test
    void le_comptage_refuse_le_negatif_et_plus_de_trois_decimales() {
        LigneDInventaire l = ligne(article, "L1", "3");
        Inventaire inv = ouvert(l);
        Inventaire compte = inv.compter(l.id(), new BigDecimal("2.125"), " casse ", "inf", MAINTENANT);
        assertEquals(0, new BigDecimal("2.125").compareTo(compte.ligne(l.id()).quantiteComptee()));
        assertEquals("casse", compte.ligne(l.id()).motifEcart());
        assertNull(inv.ligne(l.id()).quantiteComptee(), "l'inventaire d'origine n'est pas modifié");
        assertEquals("BUSINESS_ERROR", code(() -> inv.compter(l.id(), new BigDecimal("-1"), null, "inf", MAINTENANT)));
        assertEquals("INVENTORY_QUANTITY_SCALE", code(() -> inv.compter(l.id(), new BigDecimal("1.2345"), null, "inf", MAINTENANT)));
        assertEquals("INVENTORY_LINE_NOT_FOUND", code(() -> inv.compter(UUID.randomUUID(), BigDecimal.ONE, null, "inf", MAINTENANT)));
    }

    @Test
    void un_lot_ajoute_est_compte_refuse_en_doublon_et_seul_il_se_retire() {
        LigneDInventaire theorique = ligne(article, "L1", "3");
        Inventaire inv = ouvert(theorique);
        LigneDInventaire nouveau = LigneDInventaire.theorique(UUID.randomUUID(), article, "A", "Article", "u", null, "l-neuf", null,
                BigDecimal.ZERO, BigDecimal.TEN);
        Inventaire avec = inv.ajouterLigne(nouveau, new BigDecimal("2"), "RETOUR", "inf", MAINTENANT);
        assertEquals(2, avec.lignes().size());
        LigneDInventaire ajoutee = avec.ligne(nouveau.id());
        assertTrue(ajoutee.ajoutee());
        assertEquals(0, new BigDecimal("2").compareTo(ajoutee.ecart()));
        assertEquals("INVENTORY_LINE_DUPLICATE", code(() -> avec.ajouterLigne(
                LigneDInventaire.theorique(UUID.randomUUID(), article, "A", "Article", "u", null, " L-NEUF ", null, BigDecimal.ZERO, BigDecimal.ZERO),
                BigDecimal.ONE, null, "inf", MAINTENANT)));
        assertEquals("INVENTORY_LINE_DUPLICATE", code(() -> inv.ajouterLigne(
                LigneDInventaire.theorique(UUID.randomUUID(), article, "A", "Article", "u", null, "l1", null, BigDecimal.ZERO, BigDecimal.ZERO),
                BigDecimal.ONE, null, "inf", MAINTENANT)));
        assertEquals("INVENTORY_LINE_NOT_REMOVABLE", code(() -> avec.retirerLigne(theorique.id())));
        assertEquals(1, avec.retirerLigne(nouveau.id()).lignes().size());
    }

    @Test
    void le_report_du_theorique_ne_touche_que_les_lignes_non_comptees() {
        LigneDInventaire a = ligne(article, "L1", "3");
        LigneDInventaire b = ligne(article, "L2", "5");
        Inventaire inv = ouvert(a, b).compter(a.id(), new BigDecimal("1"), "CASSE", "inf", MAINTENANT);
        Inventaire reporte = inv.reporterLeTheorique("pharmacien", MAINTENANT);
        assertEquals(0, BigDecimal.ONE.compareTo(reporte.ligne(a.id()).quantiteComptee()));
        assertEquals(0, new BigDecimal("5").compareTo(reporte.ligne(b.id()).quantiteComptee()));
        assertEquals(0, reporte.lignesNonComptees());
    }

    @Test
    void la_cloture_exige_tout_compte_et_chaque_ecart_motive() {
        LigneDInventaire a = ligne(article, "L1", "3");
        Inventaire inv = ouvert(a);
        assertEquals("INVENTORY_NOT_COMPLETE", code(() -> inv.cloturer("p", MAINTENANT)));
        Inventaire sansMotif = inv.compter(a.id(), new BigDecimal("2"), null, "inf", MAINTENANT);
        assertEquals("INVENTORY_GAP_WITHOUT_REASON", code(() -> sansMotif.cloturer("p", MAINTENANT)));
        Inventaire motive = inv.compter(a.id(), new BigDecimal("2"), "CASSE", "inf", MAINTENANT);
        Inventaire clos = motive.cloturer("p", MAINTENANT);
        assertEquals(StatutDInventaire.CLOTURE, clos.statut());
        assertEquals("p", clos.clotureePar());
        assertEquals("INVENTORY_NOT_IN_PROGRESS", code(() -> clos.cloturer("p", MAINTENANT)));
        assertEquals("INVENTORY_NOT_IN_PROGRESS", code(() -> clos.annuler("p", MAINTENANT)));
        assertEquals("INVENTORY_NOT_IN_PROGRESS", code(() -> clos.compter(a.id(), BigDecimal.ONE, null, "inf", MAINTENANT)));
    }

    @Test
    void l_annulation_est_definitive() {
        Inventaire annule = ouvert(ligne(article, "L1", "3")).annuler("p", MAINTENANT);
        assertEquals(StatutDInventaire.ANNULE, annule.statut());
        assertEquals("INVENTORY_NOT_IN_PROGRESS", code(() -> annule.cloturer("p", MAINTENANT)));
    }

    @Test
    void la_valeur_de_l_ecart_est_arrondie_au_centime_au_pmp_de_l_ouverture() {
        LigneDInventaire l = LigneDInventaire.theorique(UUID.randomUUID(), article, "A", "Article", "u", null, null, null,
                new BigDecimal("3"), new BigDecimal("5.3333"));
        assertNull(l.valeurEcart());
        LigneDInventaire comptee = l.compter(new BigDecimal("1"), "CASSE", "inf", MAINTENANT);
        assertEquals(new BigDecimal("-10.67"), comptee.valeurEcart());
        assertTrue(comptee.aUnEcart());
    }

    @Test
    void seuls_les_articles_qui_ont_une_ligne_sont_inventories() {
        Inventaire inv = ouvert(ligne(article, "L1", "3"), ligne(article, "L2", "1"));
        assertEquals(1, inv.articlesInventories().size());
        assertTrue(inv.articlesInventories().contains(article));
        assertFalse(inv.articlesInventories().contains(autreArticle));
    }

    @Test
    void la_periode_controle_l_ouverture_dans_l_ordre_du_legacy() {
        PeriodeDeStock libre = new PeriodeDeStock(CENTRE, null, null);
        libre.exigerOuvrable(HIER, Optional.empty(), false, false);
        PeriodeDeStock enCours = new PeriodeDeStock(CENTRE, LocalDate.of(2026, 10, 1), "INV-00001");
        assertTrue(enCours.mouvementsBloques());
        assertEquals("INVENTORY_SORTIE_POUR_SEANCES_NON_RAPPROCHEE",
                code(() -> enCours.exigerOuvrable(HIER, Optional.of("SPS-00001"), true, true)));
        assertEquals("INVENTORY_ALREADY_IN_PROGRESS", code(() -> enCours.exigerOuvrable(HIER, Optional.empty(), true, true)));
        PeriodeDeStock close = new PeriodeDeStock(CENTRE, LocalDate.of(2026, 10, 20), null);
        assertEquals("INVENTORY_DATE_BEFORE_LAST", code(() -> close.exigerOuvrable(LocalDate.of(2026, 10, 20), Optional.empty(), true, true)));
        assertEquals("INVENTORY_MOVEMENTS_AFTER_DATE", code(() -> close.exigerOuvrable(HIER, Optional.empty(), true, true)));
        assertEquals("INVENTORY_RECALC_RUNNING", code(() -> close.exigerOuvrable(HIER, Optional.empty(), false, true)));
        close.exigerOuvrable(HIER, Optional.empty(), false, false);
    }
}

