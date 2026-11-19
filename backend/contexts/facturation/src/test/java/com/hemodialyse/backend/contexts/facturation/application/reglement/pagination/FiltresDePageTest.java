package com.hemodialyse.backend.contexts.facturation.application.reglement.pagination;

import com.hemodialyse.backend.contexts.facturation.application.reglement.pagination.FiltresDePage.Comparateur;
import com.hemodialyse.backend.contexts.facturation.application.reglement.pagination.FiltresDePage.FiltreNumerique;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Grammaire des filtres numériques des listes paginées de la Facturation (RG-REG-009/010). */
class FiltresDePageTest {

    private static FiltreNumerique nombre(String saisie) {
        return FiltresDePage.numerique("montantFacture", saisie).orElseThrow();
    }

    @Test
    void operateurs_difference_et_plages_sont_lus() {
        assertEquals(new FiltreNumerique(Comparateur.EGAL, new BigDecimal("100")), nombre("100"));
        assertEquals(new FiltreNumerique(Comparateur.SUPERIEUR_OU_EGAL, new BigDecimal("12.5")), nombre(">= 12,5"));
        assertEquals(Comparateur.INFERIEUR, nombre("<20").comparateur());
        FiltreNumerique different = nombre("!=0");
        assertEquals(Comparateur.DIFFERENT, different.comparateur());
        assertTrue(different.accepte(new BigDecimal("200.00")));
        assertFalse(different.accepte(new BigDecimal("0.00")));
        FiltreNumerique entre = nombre("100..500");
        assertEquals(new FiltreNumerique(Comparateur.ENTRE, new BigDecimal("100"), new BigDecimal("500")), entre);
        assertTrue(entre.accepte(new BigDecimal("100.00")));
        assertTrue(entre.accepte(new BigDecimal("500.00")));
        assertFalse(entre.accepte(new BigDecimal("500.01")));
        assertFalse(entre.accepte(null));
        assertEquals(new FiltreNumerique(Comparateur.SUPERIEUR_OU_EGAL, new BigDecimal("100")), nombre("100.."));
        assertEquals(new FiltreNumerique(Comparateur.INFERIEUR_OU_EGAL, new BigDecimal("500.5")), nombre("..500,5"));
        assertTrue(FiltresDePage.numerique("montantFacture", null).isEmpty());
    }

    @Test
    void toute_autre_saisie_est_refusee_avec_le_code_stable() {
        for (String saisie : List.of("beaucoup", "500..100", "..", "100..x", "!=", ">>5", "1e3", "1..2..3", "10-50", "!5")) {
            assertEquals("FILTRE_INVALIDE",
                    assertThrows(PaginationInvalide.class, () -> FiltresDePage.numerique("montantFacture", saisie)).code(), saisie);
        }
    }
}
