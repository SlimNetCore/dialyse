package com.hemodialyse.backend.contexts.stock.domain.referentiel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Conversion d'une dose en quantité de stock (RG-STK-006, utilisée par la prescription RG-MED-071).
 */
class DosageTest {

    private static String refus(Dosage dosage, String dose, String unite) {
        return assertThrows(RefusDuReferentiel.class, () -> dosage.quantitePourDose(dose == null ? null : new BigDecimal(dose), unite)).code();
    }

    @Test
    void la_dose_est_divisee_par_le_dosage_a_4_decimales_arrondi_demi_superieur() {
        Dosage epo = new Dosage(new BigDecimal("3000"), "UI", "seringue", "EPO3000");
        assertEquals(new BigDecimal("1.3333"), epo.quantitePourDose(new BigDecimal("4000"), " ui "));
        Dosage fer = new Dosage(new BigDecimal("6"), "mg", "ampoule", "FER");
        assertEquals(new BigDecimal("16.6667"), fer.quantitePourDose(new BigDecimal("100"), "mg"));
    }

    @Test
    void une_autre_unite_que_celle_du_dosage_est_refusee() {
        assertEquals("ARTICLE_UNITE_DOSE_INCOMPATIBLE", refus(new Dosage(BigDecimal.TEN, "UI", "seringue", "E"), "5", "mg"));
    }

    @Test
    void sans_dosage_seule_une_dose_dans_l_unite_de_stock_est_acceptee() {
        Dosage sans = new Dosage(null, null, "ampoule", "A");
        assertEquals(new BigDecimal("2"), sans.quantitePourDose(new BigDecimal("2"), "AMPOULE"));
        assertEquals("ARTICLE_DOSAGE_NON_DEFINI", refus(sans, "2", "mg"));
    }

    @Test
    void la_dose_doit_etre_strictement_positive() {
        Dosage d = new Dosage(BigDecimal.TEN, "UI", "seringue", "E");
        assertEquals("DOSE_ADMINISTREE_INVALIDE", refus(d, null, "UI"));
        assertEquals("DOSE_ADMINISTREE_INVALIDE", refus(d, "0", "UI"));
    }
}

