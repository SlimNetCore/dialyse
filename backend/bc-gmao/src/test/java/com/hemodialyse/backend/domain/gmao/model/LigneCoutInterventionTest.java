package com.hemodialyse.backend.domain.gmao.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LigneCoutInterventionTest {

    @Test
    void montant_should_be_quantite_times_prixUnitaire() {
        LigneCoutIntervention ligne = LigneCoutIntervention.creer(
                TypeLigneCout.PIECE, "Filtre RO", new BigDecimal("3"), new BigDecimal("450.50"), null);

        assertEquals(new BigDecimal("1351.50"), ligne.montant());
    }

    @Test
    void creer_should_reject_blank_libelle() {
        assertThrows(IllegalArgumentException.class, () -> LigneCoutIntervention.creer(
                TypeLigneCout.AUTRE, "  ", BigDecimal.ONE, BigDecimal.TEN, null));
    }

    @Test
    void creer_should_reject_non_positive_quantite() {
        assertThrows(IllegalArgumentException.class, () -> LigneCoutIntervention.creer(
                TypeLigneCout.AUTRE, "x", BigDecimal.ZERO, BigDecimal.TEN, null));
    }

    @Test
    void creer_should_reject_negative_prixUnitaire() {
        assertThrows(IllegalArgumentException.class, () -> LigneCoutIntervention.creer(
                TypeLigneCout.AUTRE, "x", BigDecimal.ONE, new BigDecimal("-1"), null));
    }
}
