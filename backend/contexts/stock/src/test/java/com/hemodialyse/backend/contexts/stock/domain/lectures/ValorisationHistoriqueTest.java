package com.hemodialyse.backend.contexts.stock.domain.lectures;

import com.hemodialyse.backend.contexts.stock.domain.lectures.ValorisationHistorique.Valorisation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Valorisation historique (RG-STK-043), mêmes scénarios que l'ancien {@code ValorisationStockCalculatorTest} de bc-stock.
 */
class ValorisationHistoriqueTest {

    private static final UUID ARTICLE = UUID.randomUUID();
    private static final OffsetDateTime JAN = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime FEV = OffsetDateTime.of(2026, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime MARS = OffsetDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private static MouvementDeStock mouvement(MouvementDeStock.Type type, String q, String pu, OffsetDateTime le) {
        return new MouvementDeStock(UUID.randomUUID(), ARTICLE, UUID.randomUUID(), null, type, new BigDecimal(q),
                pu == null ? null : new BigDecimal(pu), le, null);
    }

    private static MouvementDeStock entree(String q, String pu, OffsetDateTime le) {
        return mouvement(MouvementDeStock.Type.ENTREE, q, pu, le);
    }

    private static MouvementDeStock sortie(String q, OffsetDateTime le) {
        return mouvement(MouvementDeStock.Type.SORTIE, q, null, le);
    }

    private static MouvementDeStock inventaire(String q, String pmp, OffsetDateTime le) {
        return mouvement(MouvementDeStock.Type.INVENTAIRE, q, pmp, le);
    }

    private static void montant(String attendu, BigDecimal reel) {
        assertEquals(0, new BigDecimal(attendu).compareTo(reel), "attendu " + attendu + " mais " + reel);
    }

    @Test
    void valeur_de_debut_flux_et_valeur_de_fin_de_la_periode() {
        // 10 @ 100 avant la période ; en février : entrée 10 @ 200, sortie de 5 au PMP (150)
        Valorisation v = ValorisationHistorique.calculer(List.of(entree("10", "100", JAN.plusDays(5)),
                entree("10", "200", FEV.plusDays(3)), sortie("5", FEV.plusDays(10))), FEV, MARS);

        montant("1000", v.valeurDebut());
        montant("2000", v.entrees());
        montant("750", v.sorties());
        montant("0", v.autresVariations());
        montant("2250", v.valeurFin());
        montant("15", v.quantiteFin());
    }

    @Test
    void les_mouvements_apres_la_fin_de_periode_sont_ignores() {
        Valorisation v = ValorisationHistorique.calculer(List.of(entree("10", "100", JAN.plusDays(5)),
                entree("10", "300", MARS.plusDays(2))), FEV, MARS);

        montant("1000", v.valeurDebut());
        montant("1000", v.valeurFin());
        montant("0", v.entrees());
    }

    @Test
    void un_inventaire_repart_du_stock_compte_et_l_ecart_va_dans_les_autres_variations() {
        Valorisation v = ValorisationHistorique.calculer(List.of(entree("10", "100", JAN.plusDays(5)),
                inventaire("8", "100", FEV.plusDays(14))), FEV, MARS);

        montant("1000", v.valeurDebut());
        montant("800", v.valeurFin());
        montant("0", v.entrees());
        montant("-200", v.autresVariations());
    }

    @Test
    void plusieurs_lots_d_un_meme_inventaire_s_additionnent() {
        OffsetDateTime le = FEV.plusDays(14);
        Valorisation v = ValorisationHistorique.calculer(List.of(entree("10", "100", JAN.plusDays(5)), inventaire("3", "100", le),
                inventaire("4", "100", le)), FEV, MARS);

        montant("700", v.valeurFin());
        montant("7", v.quantiteFin());
    }

    @Test
    void un_retour_est_une_sortie_negative_au_pmp_qui_le_precede() {
        Valorisation v = ValorisationHistorique.calculer(List.of(entree("10", "100", JAN.plusDays(5)),
                sortie("4", FEV.plusDays(2)), mouvement(MouvementDeStock.Type.RETOUR, "1", "90", FEV.plusDays(3))), FEV, MARS);

        montant("300", v.sorties());
        montant("700", v.valeurFin());
        montant("0", v.autresVariations());
    }

    @Test
    void un_article_sans_mouvement_vaut_zero() {
        Valorisation v = ValorisationHistorique.calculer(List.of(), FEV, MARS);

        montant("0", v.valeurDebut());
        montant("0", v.valeurFin());
    }
}
