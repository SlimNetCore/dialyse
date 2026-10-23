package com.hemodialyse.backend.contexts.stock.domain.lectures;

import com.hemodialyse.backend.contexts.stock.domain.lectures.CalculDuPmp.Etape;
import com.hemodialyse.backend.contexts.stock.domain.lectures.MouvementDeStock.Type;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rejeu du PMP (RG-STK-060, 062) : entrées pondérées, sorties au PMP courant, stock ramené à zéro, ordre du jour.
 */
class CalculDuPmpTest {

    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-01T12:00:00Z");

    private static MouvementDeStock mouvement(Type type, String quantite, String prix, OffsetDateTime quand) {
        return new MouvementDeStock(UUID.randomUUID(), UUID.randomUUID(), null, null, type, new BigDecimal(quantite),
                prix == null ? null : new BigDecimal(prix), quand, quand);
    }

    @Test
    void une_entree_recalcule_le_pmp_et_une_sortie_le_garde() {
        List<Etape> etapes = CalculDuPmp.expliquer(List.of(mouvement(Type.ENTREE, "100", "8", T0),
                mouvement(Type.ENTREE, "50", "11", T0.plusDays(1)), mouvement(Type.SORTIE, "30", null, T0.plusDays(2))));
        assertEquals(new BigDecimal("9.0000"), etapes.get(1).apres().pmp());
        assertEquals(new BigDecimal("9.0000"), etapes.get(2).apres().pmp());
        assertEquals(new BigDecimal("120"), etapes.get(2).apres().quantite().stripTrailingZeros().setScale(0));
        assertEquals("SORTIE : 30 unite(s) valorisee(s) au PMP courant 9.0000 ; PMP inchange = 9.0000", etapes.get(2).formule());
        assertTrue(etapes.get(0).formule().startsWith("ENTREE : PMP = (0 + 100 x 8) / (0 + 100) = 800 / 100 = 8.0000"));
    }

    @Test
    void une_sortie_superieure_au_stock_remet_la_valeur_a_zero_et_garde_le_dernier_pmp() {
        List<Etape> etapes = CalculDuPmp.expliquer(List.of(mouvement(Type.ENTREE, "10", "4", T0),
                mouvement(Type.SORTIE, "500", null, T0.plusDays(1))));
        assertEquals(BigDecimal.ZERO, etapes.get(1).apres().quantite());
        assertEquals(BigDecimal.ZERO, etapes.get(1).apres().valeur());
        assertEquals(new BigDecimal("4.0000"), etapes.get(1).apres().pmp());
    }

    @Test
    void un_ajustement_est_une_entree_seulement_s_il_porte_un_prix() {
        assertTrue(mouvement(Type.AJUSTEMENT, "1", "2", T0).estUneEntree());
        assertTrue(!mouvement(Type.AJUSTEMENT, "1", null, T0).estUneEntree());
        assertTrue(mouvement(Type.INVENTAIRE, "1", null, T0).estUneEntree());
    }

    @Test
    void a_date_et_heure_egales_les_entrees_precedent_les_sorties() {
        List<MouvementDeStock> ordre = new ArrayList<>(List.of(mouvement(Type.SORTIE, "5", null, T0),
                mouvement(Type.ENTREE, "10", "3", T0)));
        ordre.sort(MouvementDeStock.CHRONOLOGIE);
        assertEquals(Type.ENTREE, ordre.get(0).type());
        List<Etape> etapes = CalculDuPmp.expliquer(ordre);
        assertEquals(new BigDecimal("3.0000"), etapes.get(1).avant().pmp());
    }
}

