package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.stock.model.StockMovement;
import com.hemodialyse.backend.domain.stock.service.ValorisationStockCalculator.Valorisation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValorisationStockCalculatorTest {

    private static final UUID CENTRE = UUID.randomUUID();
    private static final UUID ARTICLE = UUID.randomUUID();
    private static final OffsetDateTime JAN = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FEV = OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime MARS = OffsetDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private static StockMovement entree(String q, String pu, OffsetDateTime at) {
        return StockMovement.entree(CENTRE, ARTICLE, UUID.randomUUID(), new BigDecimal(q), new BigDecimal(pu), "t", at);
    }

    private static StockMovement sortie(String q, OffsetDateTime at) {
        return StockMovement.sortie(CENTRE, ARTICLE, null, new BigDecimal(q), "t", at);
    }

    private static StockMovement inventaire(String q, String pmp, OffsetDateTime at) {
        return StockMovement.inventaire(CENTRE, ARTICLE, UUID.randomUUID(), new BigDecimal(q), new BigDecimal(pmp), "t", at);
    }

    private static void assertMontant(String attendu, BigDecimal reel) {
        assertEquals(0, new BigDecimal(attendu).compareTo(reel), "attendu " + attendu + " mais " + reel);
    }

    @Test
    void should_value_opening_flows_and_closing_stock_over_the_period() {
        // 10 @ 100 avant la période ; en février : entrée 10 @ 200, sortie de 5 au PMP (150)
        List<StockMovement> mvts = List.of(
                entree("10", "100", JAN.plusDays(5)),
                entree("10", "200", FEV.plusDays(3)),
                sortie("5", FEV.plusDays(10)));

        Valorisation v = ValorisationStockCalculator.calculer(mvts, FEV, MARS);

        assertMontant("1000", v.valeurDebut());
        assertMontant("2000", v.entrees());
        assertMontant("750", v.sorties());
        assertMontant("0", v.autresVariations());
        assertMontant("2250", v.valeurFin());
        assertMontant("15", v.quantiteFin());
    }

    @Test
    void should_ignore_movements_after_the_period_end() {
        List<StockMovement> mvts = List.of(entree("10", "100", JAN.plusDays(5)), entree("10", "300", MARS.plusDays(2)));

        Valorisation v = ValorisationStockCalculator.calculer(mvts, FEV, MARS);

        assertMontant("1000", v.valeurDebut());
        assertMontant("1000", v.valeurFin());
        assertMontant("0", v.entrees());
    }

    @Test
    void an_inventory_restarts_from_the_counted_stock_and_the_gap_is_reported_as_other_variations() {
        // 10 @ 100 puis, en février, inventaire : 8 comptés (2 manquants) à PMP 100
        List<StockMovement> mvts = List.of(
                entree("10", "100", JAN.plusDays(5)),
                inventaire("8", "100", FEV.plusDays(14)));

        Valorisation v = ValorisationStockCalculator.calculer(mvts, FEV, MARS);

        assertMontant("1000", v.valeurDebut());
        assertMontant("800", v.valeurFin());
        assertMontant("0", v.entrees());
        assertMontant("-200", v.autresVariations());
    }

    @Test
    void several_lots_of_the_same_inventory_add_up_without_resetting_each_other() {
        OffsetDateTime at = FEV.plusDays(14);
        List<StockMovement> mvts = List.of(
                entree("10", "100", JAN.plusDays(5)),
                inventaire("3", "100", at),
                inventaire("4", "100", at));

        Valorisation v = ValorisationStockCalculator.calculer(mvts, FEV, MARS);

        assertMontant("700", v.valeurFin());
        assertMontant("7", v.quantiteFin());
    }

    @Test
    void an_article_without_movement_is_worth_zero() {
        Valorisation v = ValorisationStockCalculator.calculer(List.of(), FEV, MARS);

        assertMontant("0", v.valeurDebut());
        assertMontant("0", v.valeurFin());
    }

    @Test
    void should_sum_valorisations() {
        Valorisation a = ValorisationStockCalculator.calculer(List.of(entree("1", "100", JAN.plusDays(1))), FEV, MARS);
        Valorisation b = ValorisationStockCalculator.calculer(List.of(entree("2", "50", JAN.plusDays(1))), FEV, MARS);

        assertMontant("200", a.plus(b).valeurFin());
    }
}
