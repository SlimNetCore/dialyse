package com.hemodialyse.backend.contexts.stock.application.pagination;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemandeDePageTest {

    private static final Set<String> CHAMPS = Set.of("reference", "date");

    private static String code(Runnable appel) {
        return assertThrows(PaginationInvalide.class, appel::run).code();
    }

    @Test
    void valeurs_par_defaut_et_tri_par_defaut() {
        DemandeDePage d = DemandeDePage.lire(null, null, null, CHAMPS, "date", true);
        assertEquals(new DemandeDePage(0, 20, "date", true), d);
        assertEquals(0, d.decalage());
    }

    @Test
    void tri_lu_dans_la_liste_blanche() {
        assertEquals(new DemandeDePage(2, 50, "reference", false), DemandeDePage.lire(2, 50, "reference", CHAMPS, "date", true));
        assertEquals(new DemandeDePage(0, 10, "reference", true), DemandeDePage.lire(0, 10, "reference,DESC", CHAMPS, "date", false));
        assertEquals(100, DemandeDePage.lire(1, 100, null, CHAMPS, "date", false).decalage());
    }

    @Test
    void refus_avec_codes_stables() {
        assertEquals("PAGE_INVALIDE", code(() -> DemandeDePage.lire(-1, 20, null, CHAMPS, "date", true)));
        assertEquals("TAILLE_INVALIDE", code(() -> DemandeDePage.lire(0, 0, null, CHAMPS, "date", true)));
        assertEquals("TAILLE_INVALIDE", code(() -> DemandeDePage.lire(0, 101, null, CHAMPS, "date", true)));
        assertEquals("TRI_INVALIDE", code(() -> DemandeDePage.lire(0, 20, "id;DROP TABLE x", CHAMPS, "date", true)));
        assertEquals("TRI_INVALIDE", code(() -> DemandeDePage.lire(0, 20, "date,up", CHAMPS, "date", true)));
        assertEquals("TRI_INVALIDE", code(() -> DemandeDePage.lire(0, 20, "date,asc,x", CHAMPS, "date", true)));
        assertEquals("TRI_INVALIDE", code(() -> DemandeDePage.lire(0, 20, "date", Set.of(), "date", true)));
    }

    enum Statut { BROUILLON, VALIDE }

    @Test
    void filtres_stricts() {
        assertTrue(FiltresDePage.texte("  ").isEmpty());
        assertEquals("br-01", FiltresDePage.texte(" BR-01 ").orElseThrow());
        FiltresDePage.FiltreNumerique f = FiltresDePage.numerique("pmp", ">= 1,5").orElseThrow();
        assertTrue(f.accepte(new BigDecimal("1.5")));
        assertFalse(f.accepte(new BigDecimal("1.49")));
        assertTrue(FiltresDePage.numerique("pmp", "12").orElseThrow().accepte(new BigDecimal("12.00")));
        assertEquals("FILTRE_INVALIDE", code(() -> FiltresDePage.numerique("pmp", "abc")));
        assertEquals(EnumSet.of(Statut.BROUILLON, Statut.VALIDE), FiltresDePage.enumeration("statut", "BROUILLON,VALIDE", Statut.class));
        assertEquals("FILTRE_INVALIDE", code(() -> FiltresDePage.enumeration("statut", "RECU", Statut.class)));
    }

    @Test
    void decoupage_en_memoire_des_donnees_calculees() {
        PageDeResultats<Integer> page = PageDeResultats.decouper(List.of(1, 2, 3, 4, 5), new DemandeDePage(1, 2, "x", false));
        assertEquals(List.of(3, 4), page.items());
        assertEquals(5, page.total());
        assertEquals(List.of(), PageDeResultats.decouper(List.of(1), new DemandeDePage(3, 2, "x", false)).items());
    }
}
